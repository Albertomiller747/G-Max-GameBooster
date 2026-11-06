# G Max Gaming Hub — Zero-Lag Controller Overlay Pass-Through, Single-Line LED Color Readout & Unified 24-Hour Rewarded Ad Unlock

This update eliminates out-of-app frame choppiness and input delay when the Controller Overlay is active on the Android Home Screen and in games, keeps the Controller LED hex color code on a single clean line, and synchronizes the 24-hour Rewarded Ad unlock across all overlay entry points with clear unlock status feedback.

## User Review & Critical Decisions

> [!IMPORTANT]
> Both design and architecture choices have been confirmed and incorporated into this plan:

- **Confirmed Decision 1 (Rewarded Ad & 24-Hour Unlock Behavior)**: Keep the 24-hour unlock timer after watching one Rewarded Ad (which is why the ad did not pop up again while your 24-hour unlock pass remained active), and synchronize all Controller Overlay toggles across both the **Extras > Controller** tab and the **Controls & Overlay** screen with a clear active countdown badge on the overlay card.
- **Confirmed Decision 2 (Zero-Lag Out-of-App Controller Pass-Through)**: Replace the 50% duty-cycle focus loop with an **Ultra-Fast 14ms Micro-Peek** over a 120ms pass-through cycle (~88% uninterrupted game/Home Screen focus) and immediate 115ms input-cooldown handoff, while decoupling 60 FPS canvas rendering from `WindowManager.updateViewLayout` IPC calls.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Single-Line Controller LED Hex Readout**: Ensures the active LED hex color code (e.g., `#00E5FF`) next to the color swatch in the **LED Lightbar & Gyroscope Studio** card never wraps onto 2 lines on compact screens.
  2. **Zero-Lag 14ms Micro-Peek Overlay Pass-Through**: Eliminates frame choppiness and delayed presses on the Android Home Screen and inside games by reducing window focus sampling to an imperceptible 14ms micro-peek, throttling `WindowManager` layout flag updates, and immediately locking focus to the active game/Home Screen during rapid button combos.
  3. **Unified 24-Hour Rewarded Ad Gate & Status Badge**: Reactive state synchronization across all Controller Overlay switches so enabling the overlay triggers the Rewarded Ad whenever the 24-hour unlock has expired, skips the ad seamlessly while the 24-hour pass is active, and displays the remaining unlock hours directly on the Controller Overlay card.
- **Target Audience / Persona**: Mobile gamers using wired USB controllers with G Max Gaming Hub who want responsive, stutter-free Home Screen navigation and in-game controls alongside real-time button press visualization.
- **Key Value**: Instantaneous controller response in games and on the Android Home Screen, crisp single-line telemetry typography, and transparent 24-hour Rewarded Ad unlock state.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **LED Lightbar & Gyroscope Studio**: Selecting any of the 12 presets or dragging the Custom RGB sliders updates the circular color swatch and displays the 7-character hex code (`#00E5FF`) on a single non-wrapping line aligned to the right of the header.
  2. **Out-of-App & In-Game Controller Overlay**: Navigating the Android Home Screen or playing a game with the floating Controller Overlay active feels smooth and immediate—button presses, triggers, D-Pad, and thumbsticks register without stutter or delayed presses while lighting up on the floating HUD.
  3. **Enabling Controller Overlay & 24h Rewarded Unlock**:
     - If the 24-hour Pro Controller pass is **not** active, toggling the Controller Overlay ON (from either **Extras > Controller** or **Controls & Overlay**) opens the Rewarded Ad first, and only enables the overlay + shows the joystick notice after the reward is earned.
     - If the 24-hour Pro Controller pass **is** already active, toggling the Controller Overlay ON enables the HUD immediately without interrupting the user with another ad, and displays an `UNLOCKED (XXh LEFT)` status badge on the card header.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: High-contrast console telemetry dashboard with crisp monospace readouts.
  - *Color Palette & Mood*: Obsidian console surfaces (`#0A1424`), `HubBlue` (`#2563EB`), `LaunchGreen` (`#10B981`), `SolarGold` (`#F59E0B`), and `ElectricCrimson` (`#EF4444`).
  - *Typography & Hierarchy*: Weighted left-hand section labels paired with non-wrapping `FontFamily.Monospace` hex readouts (`maxLines = 1`, `softWrap = false`).
- **Interactive Feedback & Motion**:
  - Smooth 60 FPS thumbstick and trigger interpolation driven natively by `postInvalidateOnAnimation()` inside the hardware-accelerated overlay canvas rather than main-thread `WindowManager` layout rebuilds.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Decoupling Window Focus Micro-Peeks from 60 FPS Canvas Animation**
  - *Chosen Approach*: Use a 14ms focus micro-peek every 120ms (~11% peek / ~89% pass-through) and extend `yieldFocusUntilMs` by 115ms immediately upon any key or motion event, while only invoking `WindowManager.updateViewLayout` when the focus flag state actually transitions.
  - *Why*: Previously, toggling `FLAG_NOT_FOCUSABLE` on a 44ms/88ms (50%) duty cycle caused `WindowManagerService` and `InputDispatcher` to recompute window focus ~22 times per second, causing UI choppiness and swallowing half of initial button presses before handoff. A 14ms micro-peek with a 115ms active-input pass-through window keeps ~89%+ of frames focused on the game/Home Screen and eliminates unnecessary IPC calls.
  - *Alternatives Considered*: Keeping a 50% focus duty cycle (rejected due to noticeable input delay and Home Screen frame stutter).
- **Decision 2: Reactive 24-Hour Rewarded Ad Gate Across All Overlay Toggles**
  - *Chosen Approach*: Observe `AdMobManager.proControllerUnlockUntilMs` reactively via `collectAsState()` in both the Controller Tab and Controls & Overlay screen, gate the joystick notice dialog until the overlay actually activates, and show the active 24-hour unlock status badge directly on the Controller Overlay card.
  - *Why*: Explains clearly to the user when the 24-hour unlock is active (so they know why an ad wasn't needed) while ensuring the ad reliably triggers whenever the 24-hour window expires regardless of which screen is used.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   G Max Gaming Hub — Controller Subsystem               │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌──────────────────────┐  ┌─────────────────────────┐  ┌──────────────────┐
│  LED Lightbar Header │  │   24h Rewarded Ad Gate  │  │ Out-of-App HUD   │
│  Single-Line Layout  │  │   Reactive State Sync   │  │ 14ms Micro-Peek  │
├──────────────────────┤  ├─────────────────────────┤  ├──────────────────┤
│ • Label: weight(1f)  │  │ • Observe unlockUntilMs │  │ • 14ms peek /    │
│ • Swatch + #RRGGBB:  │  │   via collectAsState()  │  │   120ms cycle    │
│   maxLines = 1,      │  │ • Gate both Controller  │  │ • 115ms instant  │
│   softWrap = false   │  │   Tab & Overlay Screen  │  │   yield on input │
│                      │  │ • Show UNLOCKED (XXh)   │  │ • Zero redundant │
│                      │  │   badge on Overlay card │  │   WM IPC updates │
└──────────────────────┘  └─────────────────────────┘  └──────────────────┘
```

- **Data Model & State**:
  - `AdMobManager.proControllerUnlockUntilMs`: Reactive `StateFlow<Long>` storing the expiration epoch timestamp for the 24-hour Pro Controller pass (`REWARD_UNLOCK_DURATION_MS = 24h`).
  - `ControllerOverlayService.yieldFocusUntilMs`: Volatile timestamp ensuring that as soon as any button or stick event is sampled outside the app, window focus is immediately yielded back to the Home Screen or game for at least `115ms`.
- **Interactive Component & State Mapping**:
  - **LED Color Header**: Left label uses `Modifier.weight(1f).padding(end = 8.dp)` so the right-hand swatch and `selectedLedHex` (`maxLines = 1`, `softWrap = false`) always render on a single line.
  - **Controller Overlay Switch**: Checks wired USB connection first, then checks `AdMobManager.isProControllerUnlocked()`; if locked, launches `requestProControllerRewardedUnlock` and only enables the overlay + displays the joystick notice dialog once the reward callback fires.
  - **Floating Overlay Service Loop**: Samples window focus for 14ms once every 120ms when idle outside the app, yields focus within <1ms on any `dispatchKeyEvent` / `dispatchGenericMotionEvent`, and drives smooth stick interpolation via `postInvalidateOnAnimation()`.
