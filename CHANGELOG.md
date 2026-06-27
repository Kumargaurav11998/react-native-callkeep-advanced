# Changelog

All notable changes to this project will be documented in this file.

## [0.0.5] - 2026-06-27

### Added
- **SEO Optimization**: Added relevant keywords to `package.json` to improve discoverability on NPM and Google.
- **Swipe to end call**: Added swipe notification behavior to act as call end. Swiping away the incoming call notification now automatically declines the call instead of dismissing it visually and leaving the phone ringing.
- **Avatar Option**: Added support for remote `avatarUrl` in the incoming call payload.

### Fixed
- **Notification Visibility Bug**: Fixed an issue where the incoming call notification would not show up at all if the avatar option was not passed or left blank.
- **Metro Bundler Fix**: Resolved package export issues relating to Metro configuration on newer React Native versions.
