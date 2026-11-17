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
const CHARLIE_UID = "charlie_789";

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

test("Official developer (Albertomiller747@gmail.com): can claim isVerifiedDeveloper == true and grant Alberto Gift to hoseamk1@gmail.com", async () => {
  const devUid = "alberto_dev_747";
  const hoseaUid = "hosea_mk1_uid";
  const devDb = testEnv.authenticatedContext(devUid, {
    email: "Albertomiller747@gmail.com",
    email_verified: true,
  }).firestore();
  const hoseaDb = testEnv.authenticatedContext(hoseaUid, {
    email: "hoseamk1@gmail.com",
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

  // Seed Hosea's initial profile
  await assertSucceeds(
    hoseaDb.collection("users").doc(hoseaUid).set({
      ...buildValidProfile(hoseaUid, ts),
      email: "hoseamk1@gmail.com",
      username: "hoseamk1",
      gamerscore: 1200,
    })
  );

  // Official developer grants Alberto Gift (100,000 GS, 720p 60fps 6Mbps, DARK_HUB)
  await assertSucceeds(
    devDb.collection("users").doc(hoseaUid).update({
      gamerscore: 100000,
      recResolutionLabel: "720p",
      recFps: 60,
      recBitrateMbps: 6,
      themeMode: "DARK_HUB",
      updatedAt: new Date(),
    })
  );
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

test("Usernames: authenticated user can claim an available username with public profile showcase fields and look up players by username", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date();

  await assertSucceeds(
    aliceDb.collection("usernames").doc("cyber_ace").set({
      userId: ALICE_UID,
      username: "Cyber_Ace",
      displayName: "Alice Gamer",
      bio: "Competitive 120Hz FPS player",
      photoUrl: "https://example.com/alice.png",
      bannerUrl: "cyber_neon_grid",
      countryCode: "US",
      countryName: "United States",
      gamerscore: 5400,
      mostPlayedGameName: "PUBG MOBILE",
      mostPlayedMinutes: 140,
      totalPlayTimeMinutes: 320,
      selectedTags: ["fps_veteran", "120hz_enthusiast"],
      isVerifiedDeveloper: false,
      updatedAt: ts,
    })
  );

  // Bob can look up "cyber_ace" via get() to view Alice's external profile
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

test("TopGearProducts: signed-in users can read curated products, only verified developer can write", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const devDb = testEnv.authenticatedContext("alberto_dev_747", {
    email: "Albertomiller747@gmail.com",
    email_verified: true,
  }).firestore();

  const validProduct = {
    id: "cooler_neveika_core",
    category: "Cooling",
    group: "Phone Coolers",
    tier: "value",
    badge: "Best Pick",
    name: "NEVEIKA Plug-in Phone Cooler",
    image_url: "",
    benefit_line: "Steady semiconductor cooling that prevents mid-match thermal throttling.",
    benefits: [
      "Direct USB-C plug-in power for non-stop cooling",
      "Spring clip fits most phones without magnets",
      "Quiet fan blade design for voice chat clarity",
    ],
    price_level: "$$",
    best_for: "Ranked sessions where consistent frame pacing matters most.",
    trade_off: "Requires a USB power cable while playing.",
    pairs_with: "Pairs with your temperature overlay",
    affiliate_url: "https://link.amazon/B05GUSf1O",
    merchant: "Amazon",
    active: true,
    last_updated: "2026-10-31",
  };

  await assertFails(
    aliceDb.collection("top_gear_products").doc("cooler_neveika_core").set(validProduct)
  );
  await assertSucceeds(
    devDb.collection("top_gear_products").doc("cooler_neveika_core").set(validProduct)
  );
  await assertSucceeds(
    aliceDb.collection("top_gear_products").doc("cooler_neveika_core").get()
  );
  await assertSucceeds(aliceDb.collection("top_gear_products").get());
  await assertFails(
    unauthDb.collection("top_gear_products").doc("cooler_neveika_core").get()
  );
});

test("Squad Referral Loop: code registration, hardware-bound claim, and anti-fraud core action verification", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = new Date();
  const code = "GMAX-ALICE99";
  const hwHash = "hw_sha256_9f86d081884c7d659a2feaa0c55ad015";

  // 1. Alice registers her Squad Invite code
  await assertSucceeds(
    aliceDb.collection("referral_codes").doc(code).set({
      referrerUid: ALICE_UID,
      referrerUsername: "alice_gamer",
      referrerDisplayName: "Alice Gamer",
      code: code,
      passesRemaining: 3,
      validatedCount: 0,
      updatedAt: ts,
    })
  );

  // 2. Bob can look up Alice's code
  await assertSucceeds(bobDb.collection("referral_codes").doc(code).get());

  // 3. Anti-Fraud: Alice CANNOT refer herself
  await assertFails(
    aliceDb.collection("referral_activations").doc(ALICE_UID).set({
      inviteeUid: ALICE_UID,
      inviteeUsername: "alice_gamer",
      inviteeDisplayName: "Alice Gamer",
      referrerUid: ALICE_UID,
      referrerUsername: "alice_gamer",
      referrerCode: code,
      deviceFingerprintHash: hwHash,
      status: "PENDING_ACTIVATION",
      boostedMinutesCompleted: 0,
      hudMatchCompleted: false,
      activationTrigger: "NONE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 4. Anti-Fraud: Bob CANNOT create a referral already in ACTIVATED status on install
  await assertFails(
    bobDb.collection("referral_activations").doc(BOB_UID).set({
      inviteeUid: BOB_UID,
      inviteeUsername: "bob_duo",
      inviteeDisplayName: "Bob Duo",
      referrerUid: ALICE_UID,
      referrerUsername: "alice_gamer",
      referrerCode: code,
      deviceFingerprintHash: hwHash,
      status: "ACTIVATED",
      boostedMinutesCompleted: 15,
      hudMatchCompleted: true,
      activationTrigger: "1_HUD_MATCH",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 5. Bob claims the Guest Pass as PENDING_ACTIVATION and locks his hardware fingerprint
  await assertSucceeds(
    bobDb.collection("device_referral_claims").doc(hwHash).set({
      deviceFingerprintHash: hwHash,
      inviteeUid: BOB_UID,
      referrerUid: ALICE_UID,
      referrerCode: code,
      createdAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("referral_activations").doc(BOB_UID).set({
      inviteeUid: BOB_UID,
      inviteeUsername: "bob_duo",
      inviteeDisplayName: "Bob Duo",
      referrerUid: ALICE_UID,
      referrerUsername: "alice_gamer",
      referrerCode: code,
      deviceFingerprintHash: hwHash,
      status: "PENDING_ACTIVATION",
      boostedMinutesCompleted: 0,
      hudMatchCompleted: false,
      activationTrigger: "NONE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 6. Anti-Fraud: Charlie (burner account on same phone) CANNOT overwrite or claim with the same deviceFingerprintHash
  await assertFails(
    charlieDb.collection("device_referral_claims").doc(hwHash).set({
      deviceFingerprintHash: hwHash,
      inviteeUid: CHARLIE_UID,
      referrerUid: ALICE_UID,
      referrerCode: code,
      createdAt: ts,
    })
  );

  // 7. Anti-Fraud: Bob CANNOT transition to ACTIVATED with only 5 minutes and hudMatchCompleted == false
  await assertFails(
    bobDb.collection("referral_activations").doc(BOB_UID).update({
      status: "ACTIVATED",
      boostedMinutesCompleted: 5,
      hudMatchCompleted: false,
      activationTrigger: "15M_BOOSTED_PLAY",
      updatedAt: new Date(),
    })
  );

  // 8. Bob completes 1 full match with the Floating HUD active -> transitions to ACTIVATED
  await assertSucceeds(
    bobDb.collection("referral_activations").doc(BOB_UID).update({
      status: "ACTIVATED",
      boostedMinutesCompleted: 8,
      hudMatchCompleted: true,
      activationTrigger: "1_HUD_MATCH",
      updatedAt: new Date(),
    })
  );

  // 9. Alice can query her squad roster using matching referrerUid filter
  await assertSucceeds(
    aliceDb.collection("referral_activations").where("referrerUid", "==", ALICE_UID).get()
  );

  // 10. Terminal state lock: Bob cannot mutate his activation record once ACTIVATED
  await assertFails(
    bobDb.collection("referral_activations").doc(BOB_UID).update({
      boostedMinutesCompleted: 99,
      updatedAt: new Date(),
    })
  );
});

test("Rivals Squad Competition: squad creation (150-char bio), leader-only edit/delete, 5-player cap, squad code join, username invites, and pre-set taunts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = new Date();
  const squadId = "squad_alpha_01";
  const squadCode = "RIVALS-ALPHA01";

  // 1. Reject bio longer than 150 characters
  const overlongBio = "A".repeat(151);
  await assertFails(
    aliceDb.collection("rivals_squads").doc(squadId).set({
      squadId,
      name: "Alpha Rivals",
      bio: overlongBio,
      squadCode,
      leaderUid: ALICE_UID,
      leaderUsername: "alice_gamer",
      memberUids: [ALICE_UID],
      seasonMonthKey: "2026-11",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 2. Alice creates squad with valid 150-character max bio and registers Squad Code
  await assertSucceeds(
    aliceDb.collection("rivals_squads").doc(squadId).set({
      squadId,
      name: "Alpha Rivals",
      bio: "Top 120FPS competitive squad competing for 1st place crown!",
      squadCode,
      leaderUid: ALICE_UID,
      leaderUsername: "alice_gamer",
      memberUids: [ALICE_UID],
      seasonMonthKey: "2026-11",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("rivals_squad_codes").doc(squadCode).set({
      squadCode,
      squadId,
      leaderUid: ALICE_UID,
      squadName: "Alpha Rivals",
      updatedAt: ts,
    })
  );

  // 3. Bob looks up Squad Code and auto-joins the squad
  await assertSucceeds(bobDb.collection("rivals_squad_codes").doc(squadCode).get());
  await assertSucceeds(
    bobDb.collection("rivals_squads").doc(squadId).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(),
    })
  );

  // 4. Bob (non-leader) CANNOT edit the squad name or bio or delete the squad
  await assertFails(
    bobDb.collection("rivals_squads").doc(squadId).update({
      name: "Hacked Squad Name",
      bio: "Hacked bio",
      updatedAt: new Date(),
    })
  );
  await assertFails(bobDb.collection("rivals_squads").doc(squadId).delete());

  // 5. Alice (Squad Leader) CAN edit squad name and bio in real time
  await assertSucceeds(
    aliceDb.collection("rivals_squads").doc(squadId).update({
      name: "Apex Rivals Elite",
      bio: "Updated real-time bio by Squad Leader.",
      updatedAt: new Date(),
    })
  );

  // 6. Squad cannot exceed 5 players
  await assertFails(
    aliceDb.collection("rivals_squads").doc(squadId).update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID, "u4", "u5", "u6"],
      updatedAt: new Date(),
    })
  );

  // 7. Username-search Squad Invite: Alice invites Charlie -> Charlie must accept
  const inviteId = `${squadId}_${CHARLIE_UID}`;
  await assertSucceeds(
    aliceDb.collection("rivals_squad_invites").doc(inviteId).set({
      inviteId,
      squadId,
      squadName: "Apex Rivals Elite",
      inviterUid: ALICE_UID,
      inviterUsername: "alice_gamer",
      inviteeUid: CHARLIE_UID,
      inviteeUsername: "charlie_pro",
      status: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    charlieDb.collection("rivals_squad_invites").doc(inviteId).update({
      status: "ACCEPTED",
      updatedAt: new Date(),
    })
  );

  // 8. One-Click Pre-Set Taunts: Bob passes Alice and sends a valid preset taunt ("Catch me if you can")
  const tauntId = "taunt_bob_passes_alice_1";
  await assertSucceeds(
    bobDb.collection("rivals_taunts").doc(tauntId).set({
      tauntId,
      squadId,
      senderUid: BOB_UID,
      senderName: "Bob Duo",
      receiverUid: ALICE_UID,
      receiverName: "Alice Gamer",
      tauntText: "Catch me if you can",
      passEventKey: "pass_2026-11_bob_alice_1",
      createdAt: ts,
    })
  );

  // 9. Custom non-preset taunt text is strictly rejected
  await assertFails(
    bobDb.collection("rivals_taunts").doc("taunt_custom_fail").set({
      tauntId: "taunt_custom_fail",
      squadId,
      senderUid: BOB_UID,
      senderName: "Bob Duo",
      receiverUid: ALICE_UID,
      receiverName: "Alice Gamer",
      tauntText: "Custom text not in preset list",
      passEventKey: "pass_2026-11_bob_alice_2",
      createdAt: ts,
    })
  );

  // 10. Alice (Squad Leader) can delete the squad and squad code from Firebase database
  await assertSucceeds(aliceDb.collection("rivals_squad_codes").doc(squadCode).delete());
  await assertSucceeds(aliceDb.collection("rivals_squads").doc(squadId).delete());
});

