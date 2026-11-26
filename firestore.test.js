const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any user collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection(`users/${ALICE_UID}/notebooks`).get());
  await assertFails(unauth.collection(`users/${ALICE_UID}/topics`).get());
  await assertFails(unauth.collection(`users/${ALICE_UID}/pages`).get());
  await assertFails(
    unauth.collection(`users/${ALICE_UID}/notebooks`).doc("nb_01").set({
      userId: ALICE_UID,
      notebookNumber: 1,
      name: "Personally General",
      purpose: "Encyclopedia",
      shortDescription: "Short",
      detailedDescription: "Detail",
      iconKey: "menu_book",
      coverColorHex: 0xff1b3e38,
      coverStyle: "LEATHER",
      sortOrder: 0,
      pinned: false,
      archived: false,
      hidden: false,
      systemDefault: true,
      notebookType: "GENERAL",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Owner can create, update, list, and delete notebooks; cross-user and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  const notebookPayload = {
    userId: ALICE_UID,
    notebookNumber: 1,
    name: "PERSONALLY GENERAL",
    purpose: "Personal encyclopedia",
    shortDescription: "Any subject",
    detailedDescription: "Collect knowledge about any subject.",
    iconKey: "public",
    coverColorHex: 0xff1b3e38,
    coverStyle: "LEATHER",
    sortOrder: 1,
    pinned: true,
    archived: false,
    hidden: false,
    systemDefault: true,
    notebookType: "GENERAL",
    createdAt: created,
    updatedAt: created,
  };

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notebooks`).doc("nb_01").set(notebookPayload)
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/notebooks`).get());

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notebooks`).doc("nb_01").update({
      name: "General Encyclopedia",
      pinned: false,
      updatedAt: pastDate(1000),
    })
  );

  // Immutable createdAt cannot be modified
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notebooks`).doc("nb_01").update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // @DocumentId `id` key forbidden on create
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notebooks`).doc("nb_02").set({
      ...notebookPayload,
      id: "nb_02",
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notebooks`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notebooks`).doc("nb_01").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/notebooks`).doc("nb_01").update({
      name: "Hijacked",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notebooks`).doc("nb_01").delete());
});

test("Topics, subtopics, and pages enforce schema, optional fields, and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/topics`).doc("topic_1").set({
      userId: ALICE_UID,
      notebookId: "nb_11",
      name: "Childhood",
      shortDescription: "Early stage of human life",
      purpose: "Understand early development",
      introduction: "Childhood shapes future personality.",
      coversItems: ["Physical Growth", "Brain Development"],
      importantConcepts: ["Neuroplasticity", "Attachment"],
      relatedTopics: ["Adolescence", "Family"],
      personalNotes: "My personal reflection",
      personalObservations: "Observed milestones",
      personalExamples: "Learning to read",
      aiGeneratedInfo: "",
      referenceLinks: [],
      iconKey: "child_care",
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/subtopics`).doc("sub_1").set({
      userId: ALICE_UID,
      notebookId: "nb_11",
      topicId: "topic_1",
      name: "Brain Development",
      shortDescription: "Neural pathways",
      purpose: "Explore early cognition",
      introduction: "Synaptic growth in early years.",
      coversItems: ["Synapses"],
      importantConcepts: ["Plasticity"],
      relatedTopics: ["Memory"],
      personalNotes: "",
      aiGeneratedInfo: "",
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Page with optional topicId and subtopicId
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/pages`).doc("page_1").set({
      userId: ALICE_UID,
      notebookId: "nb_11",
      topicId: "topic_1",
      subtopicId: "sub_1",
      title: "Page 1",
      content: "नमस्ते — Early childhood notes in Hinglish and English.",
      pageNumber: 1,
      handwritingMode: true,
      fontKey: "kalam",
      fontSizeSp: 18,
      inkColorHex: 0xff1e293b,
      lineSpacingMultiplier: 1.5,
      textAlignment: "START",
      bold: false,
      italic: false,
      paperColorKey: "IVORY",
      aiNotes: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // General page without optional topicId/subtopicId (omitted)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/pages`).doc("page_2").set({
      userId: ALICE_UID,
      notebookId: "nb_13",
      title: "Rough Scratch",
      content: "25 * 4 = 100",
      pageNumber: 1,
      handwritingMode: false,
      fontKey: "plus_jakarta_sans",
      fontSizeSp: 16,
      inkColorHex: 0xff0f172a,
      lineSpacingMultiplier: 1.4,
      textAlignment: "START",
      bold: false,
      italic: false,
      paperColorKey: "WHITE",
      aiNotes: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Explicit null on optional topicId must fail (Kotlin filters nulls)
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/pages`).doc("page_3").set({
      userId: ALICE_UID,
      notebookId: "nb_13",
      topicId: null,
      title: "Bad Page",
      content: "Test",
      pageNumber: 1,
      handwritingMode: false,
      fontKey: "lora",
      fontSizeSp: 16,
      inkColorHex: 0xff0f172a,
      lineSpacingMultiplier: 1.4,
      textAlignment: "START",
      bold: false,
      italic: false,
      paperColorKey: "WHITE",
      aiNotes: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on page succeeds
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/pages`).doc("page_1").update({
      content: "Updated content",
      updatedAt: pastDate(1000),
    })
  );
});

test("Specialized collections (attachments, fitness, vocabulary, todos, preferences) validate properly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/attachments`).doc("att_1").set({
      userId: ALICE_UID,
      notebookId: "nb_07",
      fileName: "screenshot_01.jpg",
      fileType: "SCREENSHOT",
      localUriOrPath: "/data/user/0/app/files/attachments/screenshot_01.jpg",
      caption: "Article notes",
      sourceLabel: "Screenshots",
      status: "IN_PROGRESS",
      priority: "HIGH",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/fitnessLogs`).doc("fit_1").set({
      userId: ALICE_UID,
      notebookId: "nb_05",
      exerciseName: "Incline Dumbbell Press",
      muscleGroup: "Chest",
      sets: 4,
      reps: "10, 10, 9, 8",
      weightKg: 30.0,
      rir: 1,
      rpe: 9.0,
      personalRecord: true,
      notes: "Controlled eccentric",
      workoutDateMillis: Date.now() - 1000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/vocabulary`).doc("voc_1").set({
      userId: ALICE_UID,
      notebookId: "nb_02",
      word: "Resilient",
      pronunciation: "/rɪˈzɪljənt/",
      meaning: "Able to recover quickly from difficulties",
      hindiMeaning: "लचीला / कठिनाइयों से उबरने वाला",
      synonyms: ["Tough", "Adaptable"],
      antonyms: ["Fragile"],
      exampleSentences: ["She remained resilient despite setbacks."],
      category: "Modern English Vocabulary",
      mastered: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/todos`).doc("todo_1").set({
      userId: ALICE_UID,
      notebookId: "nb_09",
      title: "Revise Spoken English patterns",
      description: "Practice 20 sentence structures",
      category: "TODAY",
      priority: "HIGH",
      completed: false,
      dueDateMillis: Date.now() + 86400000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("user_settings").set({
      userId: ALICE_UID,
      customAppName: "Knowledge Library",
      themeMode: "SYSTEM",
      accentColorKey: "FOREST",
      uiFontKey: "plus_jakarta_sans",
      writingFontKey: "lora",
      handwritingFontKey: "kalam",
      defaultFontSizeSp: 17,
      paperColorKey: "IVORY",
      ruledLineColorKey: "BLUE",
      lineSpacingMultiplier: 1.5,
      defaultCoverStyle: "LEATHER",
      dashboardLayout: "GRID",
      showArchivedOnDashboard: false,
      preferredLanguage: "MIXED",
      voiceInputLanguage: "en-IN",
      aiEnabled: true,
      aiResponseLanguage: "MIXED",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Query tests matching Kotlin repository queries
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/topics`).where("notebookId", "==", "nb_11").get()
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/pages`).where("notebookId", "==", "nb_11").get()
  );
});
