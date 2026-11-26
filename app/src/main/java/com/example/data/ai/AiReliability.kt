package com.example.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

/**
 * Configurable Gemini model selection and timeout/retry settings for Firebase AI Logic.
 * Both primary and secondary models are officially supported Gemini 3.x models that do NOT
 * require Blaze billing and work with Firebase App Check.
 */
data class AiModelConfig(
    val primaryModelId: String = DEFAULT_PRIMARY_MODEL,
    val secondaryModelId: String = DEFAULT_SECONDARY_MODEL,
    val primaryMaxRetries: Int = 1,
    val secondaryMaxRetries: Int = 1,
    val firstTokenTimeoutMs: Long = 14_000L,
    val interChunkStallTimeoutMs: Long = 18_000L,
    val singleAttemptTimeoutMs: Long = 45_000L,
    val totalTurnTimeoutMs: Long = 75_000L,
    val baseBackoffMs: Long = 850L,
    val maxBackoffMs: Long = 5_000L,
    val primaryOverloadCooldownMs: Long = 90_000L
) {
    companion object {
        const val DEFAULT_PRIMARY_MODEL = "gemini-3.8-flash"
        const val DEFAULT_SECONDARY_MODEL = "gemini-3.5-flash-lite"
    }
}

enum class AiErrorKind {
    HTTP_503_OVERLOAD,
    HTTP_429_RATE_LIMIT,
    QUOTA_EXHAUSTED,
    HTTP_500_SERVER_ERROR,
    HTTP_403_AUTH_OR_APPCHECK,
    HTTP_401_UNAUTHORIZED,
    INVALID_MODEL,
    INVALID_REQUEST,
    TIMEOUT,
    NETWORK_UNAVAILABLE,
    UNKNOWN
}

data class AiClassifiedError(
    val kind: AiErrorKind,
    val userMessage: String,
    val technicalSummary: String,
    val retryableOnSameModel: Boolean,
    val allowsModelFallback: Boolean,
    val triggersPrimaryCooldown: Boolean = false,
    val retryAfterMs: Long? = null
)

class NoNetworkConnectionException(
    message: String = "No active internet connection detected on device."
) : IOException(message)

class StreamStalledException(
    message: String
) : IOException(message)

interface NetworkConnectivityChecker {
    fun isNetworkAvailable(): Boolean
}

class AndroidNetworkConnectivityChecker(
    private val appContext: Context
) : NetworkConnectivityChecker {
    override fun isNetworkAvailable(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        } catch (_: Exception) {
            // Never block AI calls if ConnectivityManager throws on a customized OEM ROM
            true
        }
    }
}

/**
 * Tracks temporary overload cooldowns per model so that when the primary model is experiencing
 * a known demand spike (HTTP 503), subsequent turns route immediately to the fast secondary
 * model instead of stalling for 15+ seconds on every turn.
 */
class ModelHealthTracker(
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val cooldownUntilByModel = ConcurrentHashMap<String, Long>()

    fun markOverloaded(modelId: String, cooldownMs: Long) {
        if (cooldownMs <= 0L) return
        cooldownUntilByModel[modelId] = clockMs() + cooldownMs
    }

    fun markHealthy(modelId: String) {
        cooldownUntilByModel.remove(modelId)
    }

    fun isInCooldown(modelId: String): Boolean {
        val until = cooldownUntilByModel[modelId] ?: return false
        if (clockMs() >= until) {
            cooldownUntilByModel.remove(modelId)
            return false
        }
        return true
    }

    fun clear() {
        cooldownUntilByModel.clear()
    }
}

object AiErrorClassifier {

    private val retryAfterSecondsRegex = Regex(
        pattern = """(?:retry[-_\s]?after|retry\s+in|retrydelay)\D{0,10}(\d+(?:\.\d+)?)\s*(?:s|sec|seconds)?""",
        option = RegexOption.IGNORE_CASE
    )

    fun classify(error: Throwable): AiClassifiedError {
        val fullChainMessage = collectErrorChain(error)
        val lower = fullChainMessage.lowercase()
        val retryAfterMs = parseRetryAfterMs(fullChainMessage)

        if (error is NoNetworkConnectionException ||
            error is UnknownHostException ||
            error is ConnectException ||
            lower.contains("unable to resolve host") ||
            lower.contains("failed to connect") ||
            lower.contains("no address associated") ||
            lower.contains("network is unreachable") ||
            lower.contains("software caused connection abort") ||
            lower.contains("no active internet")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.NETWORK_UNAVAILABLE,
                userMessage = "No internet connection. Please check Wi-Fi or mobile data and tap Retry.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = false,
                allowsModelFallback = false
            )
        }

        if (error is TimeoutCancellationException ||
            error is SocketTimeoutException ||
            error is StreamStalledException ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("deadline_exceeded") ||
            lower.contains("deadline exceeded")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.TIMEOUT,
                userMessage = "Request timed out — Gemini took too long to respond. Please check your connection and tap Retry.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = false,
                allowsModelFallback = true,
                triggersPrimaryCooldown = false
            )
        }

        // HTTP 503 / High Demand / Service Unavailable
        if (lower.contains("503") ||
            lower.contains("high demand") ||
            lower.contains("overloaded") ||
            lower.contains("service unavailable") ||
            lower.contains("temporarily unavailable") ||
            lower.contains("status: unavailable") ||
            lower.contains("code: unavailable") ||
            lower.contains("model is currently experiencing")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.HTTP_503_OVERLOAD,
                userMessage = "Gemini servers are temporarily busy due to high demand. Both primary and fallback models were tried—please tap Retry shortly.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = true,
                allowsModelFallback = true,
                triggersPrimaryCooldown = true,
                retryAfterMs = retryAfterMs
            )
        }

        // Distinguish permanent/project Quota Exhaustion vs transient HTTP 429 rate-limit burst
        val isQuotaExhausted = (lower.contains("exceeded your current quota") ||
            lower.contains("quota exceeded") ||
            lower.contains("daily limit") ||
            lower.contains("billing") ||
            lower.contains("free_tier") ||
            (lower.contains("quota") && lower.contains("exhausted")))
        if (isQuotaExhausted) {
            return AiClassifiedError(
                kind = AiErrorKind.QUOTA_EXHAUSTED,
                userMessage = "Firebase project Gemini API quota is exhausted. Wait for quota reset or check Firebase Blaze/API quota settings.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = false,
                allowsModelFallback = false
            )
        }

        // Transient HTTP 429 Rate Limit
        if (lower.contains("429") ||
            lower.contains("rate limit") ||
            lower.contains("too many requests") ||
            lower.contains("resource_exhausted") ||
            lower.contains("quota")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.HTTP_429_RATE_LIMIT,
                userMessage = "Temporary AI rate limit reached. Please wait a few seconds and tap Retry.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = true,
                allowsModelFallback = true,
                triggersPrimaryCooldown = false,
                retryAfterMs = retryAfterMs
            )
        }

        // HTTP 500 / 502 / 504 Server Error
        if (lower.contains("500") ||
            lower.contains("502") ||
            lower.contains("504") ||
            lower.contains("internal error") ||
            lower.contains("internal server error") ||
            lower.contains("status: internal")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.HTTP_500_SERVER_ERROR,
                userMessage = "Gemini encountered a temporary server error. Please tap Retry.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = true,
                allowsModelFallback = true,
                retryAfterMs = retryAfterMs
            )
        }

        // HTTP 404 / Invalid or Unsupported Model ID
        if (lower.contains("404") ||
            lower.contains("model not found") ||
            lower.contains("is not found for api version") ||
            lower.contains("not supported for generatecontent") ||
            lower.contains("deprecated") ||
            lower.contains("invalid model")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.INVALID_MODEL,
                userMessage = "Configured Gemini model is currently unavailable for this Firebase project. (${fullChainMessage.take(120)})",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = false,
                allowsModelFallback = true,
                triggersPrimaryCooldown = true
            )
        }

        // HTTP 403 / App Check / Permission Denied
        if (lower.contains("403") ||
            lower.contains("app check") ||
            lower.contains("appcheck") ||
            lower.contains("attestation") ||
            lower.contains("permission_denied") ||
            lower.contains("permission denied") ||
            lower.contains("recaptcha")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.HTTP_403_AUTH_OR_APPCHECK,
                userMessage = "App verification (Firebase App Check / permissions) failed. Ensure Google Play Services and internet are active on your device, then tap Retry. (${fullChainMessage.take(110)})",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = false,
                allowsModelFallback = false
            )
        }

        // HTTP 401 / Authentication or API Configuration
        if (lower.contains("401") ||
            lower.contains("unauthenticated") ||
            lower.contains("unauthorized") ||
            lower.contains("api key not valid") ||
            lower.contains("invalid api key")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.HTTP_401_UNAUTHORIZED,
                userMessage = "Authentication or Firebase AI configuration error (401). Try signing out and back in, then tap Retry.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = false,
                allowsModelFallback = false
            )
        }

        // HTTP 400 / Invalid Request / Payload Too Large
        if (lower.contains("400") ||
            lower.contains("invalid_argument") ||
            lower.contains("invalid argument") ||
            lower.contains("bad request") ||
            lower.contains("token count exceeds") ||
            lower.contains("request payload size")
        ) {
            return AiClassifiedError(
                kind = AiErrorKind.INVALID_REQUEST,
                userMessage = "Request could not be processed by Gemini (invalid request or context too large). Try a shorter message or start a New Chat. (${fullChainMessage.take(100)})",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = false,
                allowsModelFallback = false
            )
        }

        if (error is IOException) {
            return AiClassifiedError(
                kind = AiErrorKind.NETWORK_UNAVAILABLE,
                userMessage = "Network interruption while contacting Gemini AI. Please tap Retry.",
                technicalSummary = fullChainMessage.take(180),
                retryableOnSameModel = true,
                allowsModelFallback = false
            )
        }

        return AiClassifiedError(
            kind = AiErrorKind.UNKNOWN,
            userMessage = fullChainMessage.takeIf { it.isNotBlank() }?.take(180)
                ?: "AI request failed unexpectedly. Please tap Retry.",
            technicalSummary = fullChainMessage.take(180),
            retryableOnSameModel = false,
            allowsModelFallback = false
        )
    }

    fun computeBackoffDelayMs(
        attemptIndex: Int,
        config: AiModelConfig,
        retryAfterMs: Long? = null,
        jitterFraction: Double = Random.nextDouble(0.0, 1.0)
    ): Long {
        if (retryAfterMs != null && retryAfterMs > 0L) {
            return retryAfterMs.coerceIn(300L, config.maxBackoffMs + 1_000L)
        }
        val exp = config.baseBackoffMs * (1L shl attemptIndex.coerceIn(0, 4))
        val capped = min(config.maxBackoffMs, exp)
        val jitter = (jitterFraction.coerceIn(0.0, 1.0) * 350.0).toLong()
        return capped + jitter
    }

    internal fun parseRetryAfterMs(message: String): Long? {
        val match = retryAfterSecondsRegex.find(message) ?: return null
        val seconds = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        if (seconds <= 0.0) return null
        return (seconds * 1000.0).toLong()
    }

    private fun collectErrorChain(error: Throwable): String = buildString {
        var cur: Throwable? = error
        var depth = 0
        while (cur != null && depth < 6) {
            val part = cur.localizedMessage ?: cur.message
            if (!part.isNullOrBlank()) {
                if (isNotEmpty()) append(" | ")
                append(part.trim())
            }
            cur = cur.cause
            depth++
        }
    }
}
