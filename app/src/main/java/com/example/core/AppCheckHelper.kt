package com.example.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val APP_CHECK_MODE_PREFS = "knowledge_library_appcheck_mode"
private const val KEY_HAS_VERIFIED_INTENT_DEBUG_TOKEN = "has_verified_intent_debug_token"
private const val KEY_VERIFIED_FINGERPRINT = "verified_device_fingerprint"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var installedSignature: String? = null

internal enum class AppCheckProviderMode {
    DEBUG_WITH_INTENT_TOKEN,
    DEBUG_EMULATOR_OR_PERSISTED,
    RECAPTCHA_PRODUCTION
}

internal fun isRunningOnEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    val brand = Build.BRAND.orEmpty()
    val device = Build.DEVICE.orEmpty()
    val product = Build.PRODUCT.orEmpty()
    val hardware = Build.HARDWARE.orEmpty()
    return fingerprint.startsWith("generic") ||
        fingerprint.startsWith("unknown") ||
        fingerprint.contains("emulator", ignoreCase = true) ||
        model.contains("google_sdk", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK built for", ignoreCase = true) ||
        manufacturer.contains("Genymotion", ignoreCase = true) ||
        hardware.contains("goldfish", ignoreCase = true) ||
        hardware.contains("ranchu", ignoreCase = true) ||
        product.contains("sdk_gphone", ignoreCase = true) ||
        product.contains("emulator", ignoreCase = true) ||
        product.contains("simulator", ignoreCase = true) ||
        (brand.startsWith("generic") && device.startsWith("generic"))
}

internal fun resolveAppCheckMode(
    isDebugBuild: Boolean,
    intentDebugToken: String?,
    hasVerifiedPersistedToken: Boolean,
    isEmulator: Boolean,
    recaptchaSiteKey: String?
): AppCheckProviderMode {
    if (isDebugBuild) {
        if (!intentDebugToken.isNullOrBlank()) {
            return AppCheckProviderMode.DEBUG_WITH_INTENT_TOKEN
        }
        // CRITICAL: Never install RecaptchaAppCheckProviderFactory on an Android emulator,
        // and never wipe a previously persisted emulator debug token. Recaptcha Enterprise
        // stalls indefinitely on emulators without hardware Play attestation.
        if (isEmulator || hasVerifiedPersistedToken) {
            return AppCheckProviderMode.DEBUG_EMULATOR_OR_PERSISTED
        }
        if (!recaptchaSiteKey.isNullOrBlank()) {
            return AppCheckProviderMode.RECAPTCHA_PRODUCTION
        }
        return AppCheckProviderMode.DEBUG_EMULATOR_OR_PERSISTED
    }
    return AppCheckProviderMode.RECAPTCHA_PRODUCTION
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val modePrefs = appContext.getSharedPreferences(APP_CHECK_MODE_PREFS, Context.MODE_PRIVATE)
        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
        val currentFingerprint = Build.FINGERPRINT.orEmpty()
        val storedFingerprint = modePrefs.getString(KEY_VERIFIED_FINGERPRINT, null)
        // Guard against Android Auto Backup transferring an emulator flag onto a physical phone
        val hasVerifiedPersisted = modePrefs.getBoolean(KEY_HAS_VERIFIED_INTENT_DEBUG_TOKEN, false) &&
            (storedFingerprint.isNullOrBlank() || storedFingerprint == currentFingerprint)
        val siteKey = resolveRecaptchaSiteKey(appContext)
        val emulator = isRunningOnEmulator()

        val mode = resolveAppCheckMode(
            isDebugBuild = BuildConfig.DEBUG,
            intentDebugToken = debugToken,
            hasVerifiedPersistedToken = hasVerifiedPersisted,
            isEmulator = emulator,
            recaptchaSiteKey = siteKey
        )

        val signature = when (mode) {
            AppCheckProviderMode.DEBUG_WITH_INTENT_TOKEN -> "DEBUG_INTENT:${debugToken.hashCode()}"
            AppCheckProviderMode.DEBUG_EMULATOR_OR_PERSISTED -> "DEBUG_PERSISTED"
            AppCheckProviderMode.RECAPTCHA_PRODUCTION -> "RECAPTCHA:${siteKey.orEmpty()}"
        }

        // Avoid tearing down and re-initializing an active provider factory on Activity
        // recreation or unrelated onNewIntent events.
        if (installedSignature == signature) {
            return
        }

        when (mode) {
            AppCheckProviderMode.DEBUG_WITH_INTENT_TOKEN -> {
                val debugStore = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit required before DebugAppCheckProviderFactory reads it
                debugStore.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                modePrefs.edit()
                    .putBoolean(KEY_HAS_VERIFIED_INTENT_DEBUG_TOKEN, true)
                    .putString(KEY_VERIFIED_FINGERPRINT, currentFingerprint)
                    .commit()

                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                installedSignature = signature
                Log.d(TAG, "DebugAppCheckProviderFactory installed with launch intent debug token.")
            }

            AppCheckProviderMode.DEBUG_EMULATOR_OR_PERSISTED -> {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                installedSignature = signature
                Log.d(TAG, "DebugAppCheckProviderFactory installed (emulator=$emulator, persisted=$hasVerifiedPersisted).")
            }

            AppCheckProviderMode.RECAPTCHA_PRODUCTION -> {
                if (!siteKey.isNullOrBlank()) {
                    if (!emulator && !hasVerifiedPersisted) {
                        val debugStore = appContext.getSharedPreferences(
                            "$DEBUG_STORE_PREFIX$persistenceKey",
                            Context.MODE_PRIVATE
                        )
                        if (debugStore.contains(DEBUG_SECRET_KEY)) {
                            debugStore.edit().remove(DEBUG_SECRET_KEY).commit()
                        }
                    }
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    installedSignature = signature
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is missing or blank.")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return context.getString(resId).trim().takeIf { it.isNotBlank() }
}
