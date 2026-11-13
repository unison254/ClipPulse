const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_creator_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, overrides = {}) {
  return {
    userId: uid,
    youtubeUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    videoId: "dQw4w9WgXcQ",
    videoTitle: "How Creators Build Viral Hooks",
    channelName: "Creator Lab",
    thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    videoSummary: "High retention storytelling breakdown.",
    clipCount: 3,
    createdAt: new Date(Date.now() - 2000),
    updatedAt: new Date(Date.now() - 1000),
    ...overrides,
  };
}

function validClipPayload(uid, projectId, overrides = {}) {
  return {
    userId: uid,
    projectId,
    title: "The 3-Second Hook Formula",
    hookLine: "STOP SCROLLING IF YOU EDIT VIDEO",
    startSeconds: 12,
    endSeconds: 45,
    viralScore: 94,
    viralReason: "Strong pattern interrupt and clear payoff.",
    captionStyle: "HORMOZI_POP",
    captionPosition: "BOTTOM",
    captionScale: 1.15,
    uppercaseCaptions: true,
    captions: [
      {
        id: "cue_1",
        startOffsetSec: 0.0,
        endOffsetSec: 2.4,
        text: "Most creators lose viewers in two seconds",
        highlightWord: "lose",
      },
    ],
    hashtags: ["#shorts", "#editing"],
    createdAt: new Date(Date.now() - 2000),
    updatedAt: new Date(Date.now() - 1000),
    ...overrides,
  };
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

test("Unauthenticated user cannot list or create projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID))
  );
});

test("Owner can create, list, update, and delete own project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_1");

  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertSucceeds(
    projRef.update({
      clipCount: 4,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(projRef.delete());
});

test("Cross-user access to another user's project and clips is rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID));
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .collection("clips")
      .doc("clip_1")
      .set(validClipPayload(ALICE_UID, "proj_1"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .collection("clips")
      .doc("clip_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .collection("clips")
      .doc("clip_1")
      .update({ title: "Hacked" })
  );
});

test("Shadow update with ghost field and identity spoofing are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_shadow");

  await assertFails(
    projRef.set(validProjectPayload(ALICE_UID, { isVerified: true }))
  );
  await assertFails(
    projRef.set(validProjectPayload(BOB_UID))
  );
});

test("Owner can create and update clip with captions inside existing project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_2");
  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));

  const clipRef = projRef.collection("clips").doc("clip_1");
  await assertSucceeds(clipRef.set(validClipPayload(ALICE_UID, "proj_2")));
  await assertSucceeds(
    clipRef.update({
      captionStyle: "NEON_PULSE",
      captionScale: 1.0,
      uppercaseCaptions: false,
      updatedAt: new Date(),
    })
  );
});

test("Owner can atomically batch-create project and clip together", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_batch");
  const clipRef = projRef.collection("clips").doc("clip_batch");

  const batch = aliceDb.batch();
  batch.set(projRef, validProjectPayload(ALICE_UID));
  batch.set(clipRef, validClipPayload(ALICE_UID, "proj_batch"));
  await assertSucceeds(batch.commit());
});

test("Invalid clip timestamps or invalid captionStyle are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_3");
  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));

  await assertFails(
    projRef
      .collection("clips")
      .doc("bad_time")
      .set(validClipPayload(ALICE_UID, "proj_3", { startSeconds: 50, endSeconds: 30 }))
  );
  await assertFails(
    projRef
      .collection("clips")
      .doc("bad_style")
      .set(validClipPayload(ALICE_UID, "proj_3", { captionStyle: "INVALID_STYLE" }))
  );
});
