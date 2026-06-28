# Changelog

All notable changes to this project will be documented in this file.

## [0.0.7] - 2026-06-28

### Fixed
- **Android Crash Fixes**: Resolved NullPointerExceptions in `deinitConnection` and `sendDTMF`.
- **Android Broadcast Fix**: Added missing `ACTION_WAKE_APP` filter for background wakeup.
- **Android Value Comparison**: Fixed incorrect string reference comparison for `callUUID`.
- **iOS Crash Fix**: Added bounds check to prevent `NSRangeException` in `getSelectedAudioRoute` when outputs are empty.
- **iOS Memory Leak**: Cleared `_delayedEvents` array after sending to prevent duplicate events.
- **JS Unhandled Rejection**: Fixed unhandled promise rejection in `_alert` cancel flow.
- **TypeScript Types**: Fixed module declaration name and updated static methods to instance interface for `react-native-callkeep-advanced`.

## [0.0.5] - 2026-06-27

### Added
- **SEO Optimization**: Added relevant keywords to `package.json` to improve discoverability on NPM and Google.
- **Swipe to end call**: Added swipe notification behavior to act as call end. Swiping away the incoming call notification now automatically declines the call instead of dismissing it visually and leaving the phone ringing.
- **Avatar Option**: Added support for remote `avatarUrl` in the incoming call payload.

### Fixed
- **Call Auto Decline Bug**: Fixed an issue where the call would automatically decline after 45 seconds even after being accepted.
- **Notification Visibility Bug**: Fixed an issue where the incoming call notification would not show up at all if the avatar option was not passed or left blank.
- **Metro Bundler Fix**: Resolved package export issues relating to Metro configuration on newer React Native versions.
