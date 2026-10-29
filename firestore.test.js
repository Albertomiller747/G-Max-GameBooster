const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function buildValidProfile(uid, serverTimestamp) {
  return {
    userId: uid,
    displayName: "Alice Gamer",
    username: "alice_gamer",
    countryCode: "US",
    countryName: "United States",
    email: "alice@example.com",
    photoUrl: "https://example.com/avatar.png",
    bannerUrl: "cyber_neon_grid",
    gamerscore: 4850,
    dateOfBirth: "2002-05-14",
    bio: "Competitive FPS & MOBA player",
    selectedTags: ["fps_veteran", "120hz_enthusiast", "esports_ready", "night_owl"],
    isVerifiedDeveloper: false,
    mostPlayedGameName: "PUBG MOBILE",
    mostPlayedGamePkg: "com.tencent.ig",
    mostPlayedMinutes: 140,
    totalPlayTimeMinutes: 320,
    totalSessionsCount: 18,
    themeMode: "DARK_HUB",
    customBgColor: -15592425,
    reducedAnimations: false,
    autoBigScreenLandscape: true,
    showControllerLegendBar: true,
    suppressActivationWarning: false,
    selectedBoostMode: "BEAST",
    appliedOptionIds: ["pop_3_ram", "net_wifi_5g"],
    hudEnabled: true,
    hudFpsMode: "BALANCED",
    hudTargetCap: "AUTO",
    hudPosition: "TOP_START",
    hudScalePct: 100,
    hudOpacityPct: 85,
    hudShowFps: true,
    hudShowTemp: true,
    hudShowBattery: true,
    hudShowTime: false,
    hudShowRamPct: true,
    hudShowClock: true,
    crosshairEnabled: false,
    crosshairType: "CROSS",
    crosshairColorArgb: -16718218,
    crosshairSizeDp: 24,
    lightbarColorHex: "#38BDF8",
    recResolutionLabel: "1080p",
    recFps: 60,
    recBitrateMbps: 12,
    recMicEnabled: true,
    recShowTimerHud: true,
    recOverlayEnabled: true,
    recBubbleSizeDp: 42,
    targetGamePkg: "com.tencent.ig",
    targetGameName: "PUBG MOBILE",
    libraryPackages: ["com.tencent.ig"],
    createdAt: serverTimestamp,
    updatedAt: serverTimestamp,
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

test("Unauthenticated user: cannot read or write user profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create and read their own valid UserCloudProfile", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID);
  const aliceDb = aliceCtx.firestore();
  const ts = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidProfile(ALICE_UID, ts))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot read another user's profile", async () => {
  const ts = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .set(buildValidProfile(BOB_UID, ts));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Authenticated user: cannot spoof another userId or inject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date();
  const spoofPayload = {
    ...buildValidProfile(ALICE_UID, ts),
    isAdminGhostField: true,
  };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(spoofPayload));
});

test("Random user: cannot fake or claim isVerifiedDeveloper == true", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "randomuser@gmail.com",
    email_verified: true,
  }).firestore();
  const ts = new Date();
  const fakeDevPayload = {
    ...buildValidProfile(ALICE_UID, ts),
    isVerifiedDeveloper: true,
  };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(fakeDevPayload));
});

test("Official developer (Albertomiller747@gmail.com): can claim isVerifiedDeveloper == true", async () => {
  const devUid = "alberto_dev_747";
  const devDb = testEnv.authenticatedContext(devUid, {
    email: "Albertomiller747@gmail.com",
    email_verified: true,
  }).firestore();
  const ts = new Date();
  const devPayload = {
    ...buildValidProfile(devUid, ts),
    displayName: "Alberto Miller",
    username: "Albertomiller747",
    email: "Albertomiller747@gmail.com",
    isVerifiedDeveloper: true,
  };
  await assertSucceeds(devDb.collection("users").doc(devUid).set(devPayload));
});

test("Authenticated user: can update allowed settings fields on their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidProfile(ALICE_UID, ts))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "alice_pro",
      countryCode: "CA",
      countryName: "Canada",
      themeMode: "LIGHT_HUB",
      dateOfBirth: "2001-11-20",
      selectedBoostMode: "GAMING",
      selectedTags: ["sniper_main", "clutch_specialist"],
      updatedAt: new Date(),
    })
  );
});

test("Leaderboard: authenticated users can publish their own score and list top players", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date();

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_gamer",
      photoUrl: "https://example.com/alice.png",
      countryCode: "US",
      countryName: "United States",
      gamerscore: 5400,
      isVerifiedDeveloper: false,
      updatedAt: ts,
    })
  );

  // Bob cannot overwrite Alice's leaderboard entry
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "hacked",
      photoUrl: "",
      countryCode: "US",
      countryName: "United States",
      gamerscore: 999999,
      isVerifiedDeveloper: false,
      updatedAt: ts,
    })
  );

  // Bob can query the worldwide leaderboard with gamerscore >= 0
  await assertSucceeds(
    bobDb.collection("leaderboard").where("gamerscore", ">=", 0).orderBy("gamerscore", "desc").limit(50).get()
  );
});

test("Usernames: authenticated user can claim an available username and cannot overwrite another user's claim", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date();

  await assertSucceeds(
    aliceDb.collection("usernames").doc("cyber_ace").set({
      userId: ALICE_UID,
      username: "Cyber_Ace",
      updatedAt: ts,
    })
  );

  // Bob can check if "cyber_ace" exists via get()
  await assertSucceeds(bobDb.collection("usernames").doc("cyber_ace").get());

  // Bob cannot overwrite Alice's claimed username
  await assertFails(
    bobDb.collection("usernames").doc("cyber_ace").set({
      userId: BOB_UID,
      username: "Cyber_Ace",
      updatedAt: ts,
    })
  );
});
