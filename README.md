# DEXGRAM

DEXGRAM is a privacy-first messaging and wallet app built for clean, secure communication without phone numbers, address-book uploads, or unnecessary personal data collection.

It combines end-to-end encrypted messaging, unlinkable contact identities, encrypted file sharing, voice and video calls, and a non-custodial DeFi wallet in one open-source client. The project is based on proven open-source security foundations and keeps keys on the user's device.

## What DEXGRAM Does

- **Private messaging by default** - one-to-one and group conversations with end-to-end encryption.
- **Unique identity per contact** - each conversation can use a fresh identity to reduce social graph linking.
- **Minimal metadata exposure** - no phone number requirement, no contact discovery, and no central user directory.
- **Encrypted files and media** - secure attachments, images, voice messages, and file transfers.
- **Voice and video calls** - real-time calls integrated into the same private chat experience.
- **Private and public profiles** - private profiles for unlinkable conversations, public profiles for teams, organizations, and official channels.
- **Non-custodial wallet** - users control their own keys and funds.
- **DeFi swaps** - decentralized token swaps through smart-contract-based protocols and DEX integrations.
- **Local security controls** - app lock, encrypted local storage, and optional YubiKey-backed database protection.
- **Open-source transparency** - the code can be inspected, built, and verified by the community.

## Security Model

DEXGRAM is designed around the idea that privacy must cover both message content and relationship metadata.

- Messages and files are end-to-end encrypted.
- Contact identities are not globally reused by default.
- Private profiles help prevent linking contacts together.
- Wallet keys remain on-device and are not held by DEXGRAM.
- Local data can be protected with platform security features and hardware-backed options.
- The app builds on mature cryptographic and messaging foundations, including SimpleX Chat concepts and wallet infrastructure inspired by established non-custodial tooling.

## Platforms

This repository contains the Kotlin Multiplatform client:

- **Android** app module
- **Desktop JVM** app module
- Shared Kotlin and Compose Multiplatform code in `common/`

The project uses Compose Multiplatform for UI and platform-specific implementations for Android and Desktop features.

## Repository Structure

- `common/` - shared application code, Compose UI, models, business logic, resources, and platform abstractions.
- `android/` - Android app container, manifest, services, activities, and Android packaging.
- `desktop/` - Desktop JVM entry point and packaging resources.
- `spec/` - technical documentation for architecture, APIs, state, storage, services, and product flows.
- `product/` - product concepts, user flows, views, rules, and known gaps.
- `gradle/` - Gradle wrapper and build support.

## Build Commands

```bash
# Android debug APK
./gradlew assembleDebug

# Android release APK
./gradlew assembleRelease

# Desktop distribution for the current OS
./gradlew :desktop:packageDistributionForCurrentOS

# Run desktop/JVM tests
./gradlew desktopTest

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Build native libraries for all platforms
./gradlew common:cmakeBuild -PcrossCompile

# Clean generated build output
./gradlew clean
```

## Local Configuration

Create `local.properties` from `local.properties.example` when you need local build settings:

```properties
compression.level=0
enable_debuggable=true
application_id.suffix=.debug
app.name=DEXGRAM Debug
```

`local.properties` is intentionally ignored by Git because it may contain machine-specific paths, signing settings, or local developer preferences.

## Architecture

### Modules

- `common/src/commonMain/` - cross-platform state, UI, models, resources, and shared business logic.
- `common/src/androidMain/` - Android-specific `actual` implementations and Android-only UI helpers.
- `common/src/desktopMain/` - Desktop-specific `actual` implementations and desktop integrations.
- `android/src/main/` - Android application entry points, services, manifest, and resources.
- `desktop/src/jvmMain/` - Desktop application entry point.

### Key Areas

- `common/src/commonMain/kotlin/chat/simplex/common/model/` - app state, chat model, and core API bindings.
- `common/src/commonMain/kotlin/chat/simplex/common/views/` - Compose screens for chats, calls, settings, profiles, files, and wallet features.
- `common/src/commonMain/kotlin/chat/simplex/common/platform/` - platform abstraction layer using Kotlin `expect` / `actual`.
- `common/src/commonMain/kotlin/chat/simplex/common/ui/theme/` - themes, colors, typography, and UI styling.
- `common/src/commonMain/cpp/` - native integration and build support for the core library.

## Native Integration

The app communicates with a native core library through JNI/FFI bindings.

- Android native builds use CMake under `common/src/commonMain/cpp/android/`.
- Desktop native builds use CMake under `common/src/commonMain/cpp/desktop/`.
- Generated native libraries are build artifacts and should not be committed.

### Android Haskell Core Libraries

DEXGRAM does not build the SimpleX Haskell core inside the regular Android CI job. The native libraries are built from a separate SimpleX Chat checkout. In a separate working directory, clone SimpleX Chat, copy our Android library build script into that checkout, then run it from the SimpleX repository root:

```bash
git clone https://github.com/simplex-chat/simplex-chat.git
cp build-android-libs.sh simplex-chat/scripts/android/
cd simplex-chat
ARCHES="aarch64 armv7a" ./scripts/android/build-android-libs.sh
```

That script produces the native `.so` libraries required by the Android build, including `libsimplex.so` and `libsupport.so`. After they are built, we collect the generated `.so` files and inject them into the DEXGRAM Android build under `common/src/commonMain/cpp/android/libs/<abi>/`.

The `codemagic.yaml` workflow shows the injection step used by our Android build: it downloads the prebuilt `.so` files for `arm64-v8a` and `armeabi-v7a`, verifies that they exist, and then lets Gradle/CMake package them into the APK or AAB.

Generating these Haskell libraries is slow and resource-intensive. We use a dedicated machine only for this native compilation step, and the resulting artifacts are then consumed by the Android build. This work should be done outside Codemagic; Codemagic should only receive or download the already-built native libraries.

## Testing

Tests are organized by platform:

- `common/src/commonTest/kotlin/` - shared tests.
- `common/src/desktopTest/kotlin/` - Desktop-specific tests.
- `android/src/androidTest/` - Android instrumented tests.

Useful commands:

```bash
./gradlew test
./gradlew desktopTest
./gradlew connectedAndroidTest
```

## Resources and Localization

DEXGRAM uses Moko Resources for cross-platform resource management.

- Base strings live in `common/src/commonMain/resources/MR/base/strings.xml`.
- Localized variants live alongside the base resource files.
- The `adjustFormatting` Gradle task validates resource formatting during builds.

## Android Notes

- Minimum SDK: 26
- Target SDK: 35
- NDK: 23.1.7779620
- ABI splits include `arm64-v8a` and `armeabi-v7a`.
- Android background messaging uses platform services and notification controls designed to preserve privacy without relying on contact discovery.

## Desktop Notes

- Desktop packaging supports macOS, Windows, and Linux distributions.
- macOS signing and notarization can be configured through local properties.
- Desktop media playback uses platform-specific integrations.

## Contributing

Before changing behavior, read the relevant files under `product/` and `spec/` so the implementation stays aligned with the documented product model.

Recommended workflow:

1. Read the product flow or view document that matches the feature.
2. Read the related technical spec.
3. Make the smallest focused change.
4. Run the relevant Gradle test or build task.
5. Check `git status --short` and `git diff` before committing.

## Roadmap

These are the next areas of work for DEXGRAM:

- ~~Improve **Vault** and synchronization.~~
- ~~Publish the **Vault server source code**~~: https://github.com/dexgram/public-staging-dexgram-vault-api
- ~~Work on the **Android VPN** as a separate project.~~: https://github.com/dexgram/vpn-android-asset
- Improve **voice scrambling**.
- Work on the **iOS version**.
- Work on the **Desktop version** later.

## Temporary Files and Build Output

Build artifacts, IDE state, native outputs, logs, and editor temporary files are ignored by `.gitignore`. Generated outputs should stay out of commits unless they are intentionally versioned source assets.

## Acknowledgements

DEXGRAM exists because we value the work of projects that pushed private messaging, self-custody, and hardware-backed security forward.

This project is forked from **SimpleX Chat** and **Unstoppable Wallet**, and it also uses the **YubiKey SDK** for hardware-backed security features. We deeply respect these projects and the people building them.

DEXGRAM is not a rejection of the original projects. We like them, we think they are strong, and we want to keep learning from their work. At the same time, we want the freedom to explore a different product direction, combine private communication with self-custody, simplify some decisions, and make tradeoffs that fit our own vision.

## License

DEXGRAM is licensed under the **GNU Affero General Public License v3.0** (`AGPL-3.0`).

See [LICENSE](LICENSE) for the full license text.
