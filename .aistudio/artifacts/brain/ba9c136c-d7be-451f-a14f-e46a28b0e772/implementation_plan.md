# Implementation Plan: G Max Screen Recording Suite & Game Hub Polish

This plan implements the complete in-game screen recording system, video quality controls, interactive floating recording overlay, gallery integration, and UI clean-up as requested.

## User Decisions Confirmed
- **Icon Removal**: Remove the top hamburger `[ = ]` button beside the "Games" title in `GamesScreen.kt`.
- **Navigation**: Add "Record" as a dedicated bottom navigation tab (Games, Record, Extras, Settings).
- **Video Storage**: Save videos to the device's public `Movies/GMaxRecordings` gallery via `MediaStore` with an in-app player.

---

## 1. UI & Navigation Updates
- **Remove Hamburger Icon**:
  - In `GamesScreen.kt`, remove the 46dp `[ = ]` hamburger box beside "Games" so the screen header is clean and spacious.
- **Bottom Navigation Bar**:
  - Update `MainActivity.kt` to include 4 tabs:
    1. **Games** (`nav_games`)
    2. **Record** (`nav_record`) - Icon: `Icons.Default.FiberManualRecord` / `Videocam`
    3. **Extras** (`nav_extras`)
    4. **Settings** (`nav_settings`)
- **Permission Flow on First Press**:
  - When user taps the "Record" tab, if required permissions (`RECORD_AUDIO`, `POST_NOTIFICATIONS`, and overlay `canDrawOverlays`) are not yet granted, trigger the onboarding permission launcher immediately. Once granted, proceed directly to the recording dashboard.

---

## 2. Screen Recording Hub (`ScreenRecordScreen.kt`)
- **Top Controls**:
  - Master switch: "Enable Floating Recording Widget".
  - "Auto-Configure Ideal Settings" card: Detects device screen resolution, GPU/SoC tier, and RAM to set recommended lag-free defaults (e.g. 1080p60 @ 8 Mbps for high-end, 720p60 @ 6 Mbps for mid-tier, 480p30 @ 3 Mbps for budget).
- **Video Quality Customizer**:
  - **Resolution Selector**: 480p, 540p, 640p, 720p, 1080p, 1440p, 2K, 4K (filtered based on device display resolution).
  - **Frame Rate Selector**: 30, 60, 90, 120 fps (filtered based on device `refreshRateHz`).
  - **Bitrate Slider**: 1 to 16 Mbps custom slider/stepper with current value display.
  - **Audio Source**: Microphone audio toggle.
- **Past Recordings Gallery**:
  - Loads saved videos from `MediaStore.Video.Media` (filtered to GMax recordings).
  - Cards showing video thumbnail, duration, file size, resolution, and recorded timestamp.
  - Interactive playback modal dialog with ExoPlayer / VideoView.
  - Share and delete actions.

---

## 3. Dedicated Floating Recording Overlay (`ScreenRecordOverlayService.kt`)
- Independent from the FPS HUD overlay.
- **Compact State**: Small draggable floating red/white circle (48dp).
- **Expanded State (5 Interactive Options)**:
  1. **Start / Stop Recording**: Triggers MediaProjection capture with visual countdown.
  2. **Recording Time HUD Toggle**: Pill overlay switch to show/hide live elapsed duration ("00:03:25") during gameplay.
  3. **Live Microphone Toggle**: Toggle mic audio on/off during gameplay.
  4. **Red "X" Close Button**: Closes and removes the floating overlay.
  5. **Quick Screenshot Button**: Instantly captures frame and saves to `Pictures/GMaxScreenshots`.
- **Clean Video Output**:
  - While recording, the floating recording widget collapses to a minimal transparent dot or disappears from the projection layer, ensuring no watermarks or controls appear on the final recorded video.

---

## 4. Hardware Media Engine & Permissions
- **Manifest Updates**:
  - Declare `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW`.
- **MediaProjection + MediaRecorder Pipeline**:
  - `ScreenRecordingService` manages foreground lifecycle, `MediaRecorder` setup, surface generation, and video encoding to MP4.
  - Automatic `MediaStore` insert so recorded videos immediately show in both Google Photos / Device Gallery and the in-app Record tab.

---

## Verification Plan
1. Compile build verification with `compile_applet`.
2. Unit testing with Robolectric (`gradle :app:testDebugUnitTest`) testing recording configuration presets, resolution/framerate validation, and navigation tab routing.
3. Validate permission flow handling and settings persistence.
