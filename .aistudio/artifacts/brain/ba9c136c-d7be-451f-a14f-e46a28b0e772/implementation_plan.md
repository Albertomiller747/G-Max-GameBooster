# Implementation Plan: Screen Recorder Device Audio, Google Sign-In Fix & Facebook Login

## 1. Overview & Goals
This update resolves silent gameplay recordings and authentication issues while adding Facebook Login support:
- **Screen Recorder Internal & Microphone Audio Engine**: Replaces the microphone-only recorder pipeline with a real-time internal audio (`AudioPlaybackCapture`) + microphone audio mixer and encoder so recorded gameplay videos include crisp device audio, microphone commentary, or both simultaneously.
- **Selectable Audio Source Modes**: Adds a clear 3-way selector (**Internal Audio**, **Internal + Mic**, **Mic Only**, plus **Mute**) in the Recorder Settings tab and floating overlay controls.
- **Google Sign-In Fix & Web/Account Picker Fallback**: Fixes context unwrapping, SHA-1/OAuth credential option handling, and adds an automatic fallback account picker & instant sign-in dialog when a device or emulator lacks a pre-configured Google Play Credential Manager session.
- **Facebook Login on Auth & Profile Screens**: Adds a **Continue with Facebook** button alongside Google Sign-In on both the initial Sign-In / Create Account screen and the Account & Profile screen, complete with interactive Facebook OAuth sign-in and profile synchronization.

---

## 2. Key Features & Technical Architecture

### A. Screen Recorder Device & Gameplay Audio Capture
- **Internal Device Audio (`AudioPlaybackCaptureConfiguration`)**:
  - On Android 10+ (API 29+), uses the active `MediaProjection` token to capture internal device/game audio (`USAGE_GAME`, `USAGE_MEDIA`, `USAGE_UNKNOWN`) via `AudioRecord` at 44.1 kHz stereo/mono PCM.
- **Selectable Audio Capture Modes**:
  - **`INTERNAL_ONLY` (Default)**: Records internal game and device audio without picking up background room noise.
  - **`INTERNAL_AND_MIC`**: Captures both internal device audio and physical microphone input simultaneously, mixing the PCM streams in real time before AAC encoding and muxing with the H.264 video surface.
  - **`MIC_ONLY`**: Records microphone voice commentary alongside the video capture.
  - **`MUTE`**: Records silent video only when explicitly chosen.
- **Hardware & Permission Safeguards**:
  - Automatically requests `RECORD_AUDIO` runtime permission before starting `MediaProjection` so audio capture never silently drops.
  - Removes the legacy `audioManager.isMicrophoneMute = true` call that previously muted the system audio stream when mic recording was toggled off.
  - Updates `AndroidManifest.xml` foreground service declarations to support media projection and audio capture seamlessly.

### B. Google Sign-In Fix & Automatic Fallback
- **Activity Context Unwrapping**:
  - Ensures `CredentialManager.getCredential()` always receives the unwrapped `Activity` instance via a `Context.findActivity()` helper rather than failing when called from wrapped Compose contexts.
- **Two-Stage Credential Request & Instant Fallback**:
  - First attempts `GetSignInWithGoogleOption`, then automatically retries with `GetGoogleIdOption(filterByAuthorizedAccounts = false)` if the primary request fails.
  - If the device/emulator has no Google Play Services account configured (`NoCredentialException` or configuration mismatch), automatically opens an interactive **Google Account Picker & Web Sign-In** sheet so the user can sign in immediately without getting stuck.

### C. Facebook Login Integration
- **Auth Screen & Account Profile Screen**:
  - Adds a styled **Continue with Facebook** button (`#1877F2` Facebook brand accent with vector logo) directly below the **Continue with Google** button on both `AuthScreen` and `AccountProfileScreen`.
- **OAuth & Profile Sync**:
  - Supports Facebook OAuth (`FirebaseAuth` `OAuthProvider("facebook.com")` with interactive account sign-in dialog fallback) and syncs the user's display name, email, avatar, and gamerscore profile to local preferences and cloud storage.

---

## 3. Planned File Changes
- `app/src/main/AndroidManifest.xml`: Configure foreground service audio/mediaProjection permissions for uninterrupted internal and mic audio recording.
- `app/src/main/java/com/example/model/RecorderModels.kt` & `app/src/main/java/com/example/util/ScreenRecordingManager.kt`: Add `AudioSourceMode` (`INTERNAL_ONLY`, `INTERNAL_AND_MIC`, `MIC_ONLY`, `MUTE`) defaulting to internal device audio capture.
- `app/src/main/java/com/example/service/ScreenRecordingService.kt`: Implement real-time internal device audio capture (`AudioPlaybackCaptureConfiguration`) + microphone mixing with `MediaCodec` + `MediaMuxer` (and automatic fallback) so all gameplay recordings include clear audio.
- `app/src/main/java/com/example/ui/screens/ScreenRecordScreen.kt` & `app/src/main/java/com/example/MainActivity.kt`: Add the Audio Source selector in Recorder Settings and ensure `RECORD_AUDIO` permission is requested prior to recording.
- `app/src/main/java/com/example/data/repository/UserAccountRepository.kt`: Fix Google Sign-In Activity resolution, add multi-step Credential Manager fallback + interactive Google/Facebook sign-in support.
- `app/src/main/java/com/example/ui/screens/AuthScreen.kt` & `app/src/main/java/com/example/ui/screens/AccountProfileScreen.kt`: Add **Continue with Facebook** buttons and interactive Google/Facebook sign-in sheets on both screens.
