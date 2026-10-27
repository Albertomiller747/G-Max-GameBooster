# 40+ Premade Profile Tags & Exclusive Verified Developer Gold-Star Badge

Deliver a rich **40+ Premade Gamer Tag System** allowing users to equip up to **4 custom profile tags** across five categories, alongside a pre-provisioned **Verified Developer Account** for `Albertomiller747@gmail.com` featuring a **permanent 5th Gold-Star Developer Badge** protected against spoofing by both client-side identity checks and Cloud Firestore Security Rules.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and security decisions incorporate your confirmed preferences from Phase 1:

- **Confirmed Decision 1 — Permanent 5th Gold-Star Developer Badge (`Albertomiller747@gmail.com`)**: When signed in as `Albertomiller747@gmail.com`, the profile automatically displays the **Verified Developer (`★ DEVELOPER`)** gold-star badge as a permanent 5th badge alongside the 4 customizable profile tags.
- **Confirmed Decision 2 — Anti-Spoofing & Pre-Provisioned Developer Profile**:
  - The Developer tag is **never selectable** by normal users in the tag picker (`DEVELOPER` is excluded from the public selectable tag list) and cannot be spoofed via custom display names, bios, or Firestore payloads.
  - Cloud Firestore security rules (`firestore.rules`) strictly verify `request.auth.token.email.lower() == 'albertomiller747@gmail.com'` whenever `isVerifiedDeveloper == true`, rejecting any write attempt from any other user trying to claim the Developer badge.
  - A pre-configured profile seed for `Albertomiller747@gmail.com` is built in so signing in via Google Sign-In or logging in with `Albertomiller747@gmail.com` immediately activates the Verified Developer Gold-Star status, developer title, and default signature tags.
- **Confirmed Decision 3 — 45 Premade Tags Across 5 Categories**: Users can browse, filter by category, and select up to **4 tags** from **45 curated premade tags** spanning **Playstyle**, **Genres**, **Competitive Tiers**, **Hardware**, and **Vibes**.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Exclusive Developer Account (`Albertomiller747@gmail.com`)**: Recognizes `Albertomiller747@gmail.com` as the official G-Max / TitanBooster creator account. Displays a metallic Gold-Star badge (`★ VERIFIED DEVELOPER`) with a subtle gold shimmer border and verification seal in the Profile Hero Card, plus a permanent 5th badge in the Profile Tags showcase.
  2. **45 Premade Profile Tags (Max 4 Equipped)**: Adds a **Gamer Tags** section to the Account & Profile screen where users view their equipped tags (up to 4) and open an interactive **Tag Selector Modal** organized into 5 category tabs (`Playstyle`, `Genres`, `Competitive Tiers`, `Hardware`, `Vibes`).
  3. **Cloud & Local Persistence**: Selected tags (`selectedTags`) and verified developer status (`isVerifiedDeveloper`) persist locally in `SharedPreferences` and sync automatically with Cloud Firestore (`/users/{userId}`).
- **Target Audience / Persona**: Mobile gamers personalizing their gamer identity and verifying the official app creator's profile with an unforgeable Gold-Star Developer badge.
- **Key Value**: Expressive gamer profiles with zero-trust anti-spoofing protection so no random user can impersonate the developer.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Viewing & Editing Profile Tags**:
     - On the **Account & Profile** screen, directly below the Gamer Bio card, a **Profile Tags (`X/4 Equipped`)** card displays the user's selected tag chips styled with category-coded cyber accents, plus an **Edit Tags** button.
     - Tapping **Edit Tags** opens the **Premade Tag Picker Dialog** featuring a live `Selected: X / 4` counter, category filter chips (`All`, `Playstyle`, `Genres`, `Competitive Tiers`, `Hardware`, `Vibes`), a clear-all action, and a scrollable grid of 45 premade tags.
     - Tapping a tag toggles its selection (up to 4 max; once 4 are selected, unselected tags dim with a subtle notice until one is deselected). Tapping **Save Tags** persists locally and syncs to Firestore.
  2. **Verified Developer Experience (`Albertomiller747@gmail.com`)**:
     - When `Albertomiller747@gmail.com` signs in (via Google Sign-In or the Login / Create Account screen), the app automatically provisions/recognizes the official Developer Account (`Alberto Miller`, `Albertomiller747@gmail.com`).
     - A prominent **Gold-Star Developer Badge (`★ G-MAX CREATOR • VERIFIED DEVELOPER`)** with a gold star icon and verification lock notice (`Verified via Account Identity`) appears on the profile hero card and as the permanent 5th badge alongside the 4 custom tags.
     - If any other account attempts to type "Developer" or spoof the tag, the gold-star badge never renders and Firestore rules reject `isVerifiedDeveloper: true`.

- **Visual Identity & Theme**:
  - **Developer Gold-Star Palette**: Radiant Gold (`#FFD700`), Amber Glow (`#F59E0B`), and Deep Obsidian Gold Tint (`#2A2108`) with a crisp gold star icon (`Icons.Default.Star` + `Icons.Default.Verified`).
  - **5 Premade Tag Category Accents (45 Total Tags)**:
    - **Playstyle (9 tags — Cyber Blue `#38BDF8`)**: ` aggressive_rusher` (Aggressive Rusher), `tactical_igl` (Tactical IGL), `sniper_main` (Sniper Main), `clutch_player` (Clutch Specialist), `stealth_assassin` (Stealth Assassin), `support_anchor` (Support Anchor), `solo_queue_warrior` (Solo Queue Warrior), `squad_captain` (Squad Captain), `speedrunner` (Speedrunner).
    - **Genres (9 tags — Neon Mint `#00E676`)**: `fps_veteran` (FPS Veteran), `battle_royale` (Battle Royale Dropper), `moba_strategist` (MOBA Strategist), `rpg_completionist` (RPG Completionist), `racing_pro` (Apex Racer), `fighting_game_champ` (Fighter Main), `open_world_explorer` (Open World Explorer), `survival_crafter` (Survival Crafter), `rhythm_master` (Rhythm God).
    - **Competitive Tiers (9 tags — Electric Purple `#C084FC`)**: `esports_ready` (Esports Ready), `top_1_percent` (Top 1% Ranked), `grandmaster_tier` (Grandmaster Tier), `conqueror_rank` (Conqueror Rank), `tournament_grinder` (Tournament Grinder), `scrim_regular` (Scrim Regular), `leaderboard_climber` (Leaderboard Climber), `mvp_streak` (MVP Streak), `undefeated_ace` (Undefeated Ace).
    - **Hardware & Tech (9 tags — Amber `#FBBF24`)**: `120hz_enthusiast` (120Hz Enthusiast), `claw_grip_player` (4-Finger Claw), `gyro_aim_god` (Gyro Aim Pro), `controller_pro` (Controller Pro), `max_fps_hunter` (Max EFPS Hunter), `zero_ping_purist` (Low Ping Purist), `thermal_tweaker` (Thermal Optimizer), `adb_power_user` (ADB Power User), `oled_hdr_gamer` (OLED HDR Gamer).
    - **Vibes & Community (9 tags — Coral Rose `#FB7185`)**: `night_owl_gamer` (Night Owl), `chill_vibes` (Chill Vibes), `mic_always_on` (Mic On Comms), `silent_carry` (Silent Carry), `streamer_creator` (Content Creator), `trophy_hunter` (100% Trophy Hunter), `day_one_og` (Day One OG), `weekend_grinder` (Weekend Grinder), `non_toxic_gg` (Good Vibes Only).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unforgeable Gold-Star Developer Badge Enforcement**
  - *Chosen Approach*: Bind the Developer badge strictly to the normalized email `albertomiller747@gmail.com` both in Kotlin (`DeveloperAccountVerifier.isOfficialDeveloper(email)`) and in Cloud Firestore Security Rules (`request.auth.token.email.lower() == 'albertomiller747@gmail.com'`). Additionally, sanitize `selectedTags` against the fixed whitelist of 45 premade tag IDs so no arbitrary string or `"developer"` tag ID can ever be injected into `selectedTags`.
  - *Why*: Guarantees cryptographic/rule-level authenticity—even if a malicious user modifies client requests, Firestore rejects any profile write where `isVerifiedDeveloper == true` unless the authenticated token belongs to `Albertomiller747@gmail.com`, and rejects any tag ID not in the premade catalog.
- **Decision 2: Permanent 5th Developer Badge + 4 Custom Tags**
  - *Chosen Approach*: Store the user's 4 chosen premade tag IDs in `selectedTags: List<String>` (capped at `size <= 4`), while rendering the Gold-Star Developer badge as a dedicated, permanent 5th badge for `Albertomiller747@gmail.com`.
  - *Why*: Lets the developer still customize all 4 gamer tags without losing a slot to the mandatory Developer verification badge.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         AccountProfileScreen                              │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Profile Hero Card                                                   │  │
│  │  • Avatar + Display Name + Email                                    │  │
│  │  • [If Albertomiller747@gmail.com] ★ VERIFIED DEVELOPER Gold Badge  │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Profile Tags Card ("X/4 Equipped" + Edit Tags Button)               │  │
│  │  • [Permanent 5th Badge if Developer]: ★ DEVELOPER (Gold Star)      │  │
│  │  • Up to 4 Equipped Premade Tag Chips (Category-Colored)            │  │
│  │  • Opens PremadeTagPickerDialog (45 Tags across 5 Categories)       │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     │
                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│               GameBoosterViewModel & PremadeTagsCatalog                   │
│  • PremadeTagsCatalog: 45 curated PremadeTag items across 5 categories    │
│  • DeveloperAccountVerifier: checks email == "albertomiller747@gmail.com" │
│  • Pre-provisioned Developer Account Seed for Albertomiller747@gmail.com  │
│  • selectedProfileTagIds: StateFlow<List<String>> (max 4 IDs)             │
│  • isVerifiedDeveloperAccount: StateFlow<Boolean>                         │
└──────────────────┬───────────────────────────────────┬────────────────────┘
                   │                                   │
                   ▼                                   ▼
┌─────────────────────────────────────┐ ┌───────────────────────────────────┐
│   SharedPreferences (Local Sync)    │ │ Cloud Firestore (/users/{userId}) │
│ • pref_profile_selected_tags        │ │ • selectedTags: List<String> (<=4)│
│ • Seeded default profile for        │ │ • isVerifiedDeveloper: Boolean    │
│   Albertomiller747@gmail.com        │ │ • Enforced in firestore.rules     │
└─────────────────────────────────────┘ └───────────────────────────────────┘
```

### Data Model & Security Rules Validation
- **`PremadeTag` Model**: `id: String`, `label: String`, `category: TagCategory` (`PLAYSTYLE`, `GENRES`, `COMPETITIVE`, `HARDWARE`, `VIBES`).
- **`UserCloudProfile` Updates**:
  - `selectedTags: List<String> = emptyList()` (validated against the 45 premade IDs, max 4 items).
  - `isVerifiedDeveloper: Boolean = false` (true exclusively when authenticated/signed in as `Albertomiller747@gmail.com`).
- **Firestore Security Rules (`firestore.rules`)**:
  - Add `selectedTags` (`list`, `size() <= 4`) and `isVerifiedDeveloper` (`bool`) to `allowedProfileKeys()` and `isValidUserCloudProfile(data)`.
  - Enforce `(data.isVerifiedDeveloper == false || (request.auth.token.keys().hasAny(['email']) && request.auth.token.email.lower() == 'albertomiller747@gmail.com'))` so no random user can ever set `isVerifiedDeveloper = true` in Firestore.
