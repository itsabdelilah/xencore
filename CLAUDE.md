# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AdMediation (XenCore) is an Android Ad Mediation Library that simplifies ad integration across multiple ad networks (AdMob, Meta Audience Network, AppLovin) with Firebase Remote Config support for runtime configuration. The project consists of two modules:

- **adscore/** - The main library module published to JitPack
- **app/** - Demo application showing library usage

## Build Commands

```bash
# Build the library
./gradlew adscore:build

# Build the demo app
./gradlew app:build

# Build release version
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests on device/emulator
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### Initialization Flow (Critical Order)

1. **Application.onCreate()** → `AdsManager.initWithoutFetch()` (~3ms, non-blocking)
2. **SplashActivity.onCreate()** → `AdsManager.initializeOnSplash()` (2-5s async with progress)
   - Fetches Remote Config
   - Attempts App ID override
   - Initializes consent and MobileAds SDK
3. **MainActivity** → Preload and show ads

### 3-Tier Fallback System

Ad configuration follows this priority:
1. **Firebase Remote Config** - Fetched from Firebase Console
2. **AdConfig** - App-provided hardcoded defaults
3. **AdUnits** - Google's test IDs (emergency fallback only)

### Core Components (adscore/src/main/java/io/ads/mediation/)

| File | Purpose |
|------|---------|
| `AdsManager.kt` | Singleton - Central API for all ad operations, lifecycle management, frequency capping |
| `RemoteConfigManager.kt` | Singleton - Firebase Remote Config integration with sync/async fetch |
| `AdConfig.kt` | Data class for app-specific ad unit defaults |
| `AdUnits.kt` | Google's official test ad unit IDs |
| `NativeAdBinder.kt` | Flexible native ad layout binding utility |
| `AdmobAppIdOverride.kt` | Runtime AdMob App ID override (must be called BEFORE MobileAds.initialize()) |

### Remote Config Keys

- `admob_app_id` - Runtime app ID override
- `open_ad_unit` - App Open ad unit
- `inter_ad_unit` - Interstitial ad unit
- `reward_ad_unit` - Rewarded ad unit
- `native_ad_unit` - Native ad unit
- `ad_frequency_seconds` - Ad frequency cap

## Key Patterns

- **Singleton Pattern**: All manager classes use Kotlin `object` declarations
- **Lifecycle Observer**: AdsManager implements `DefaultLifecycleObserver` for app-level events
- **Frequency Capping**: Timestamp-based, configurable interval (default 30s)
- **Timeout Handling**: All ad operations have timeouts (800-1500ms for ads, 5s for Remote Config)

## Native Ad Layout Requirements

When creating native ad layouts, use these View IDs:
- **Required**: `ad_headline`, `ad_body`, `ad_call_to_action`
- **Optional**: `ad_app_icon`, `ad_stars`, `ad_advertiser`, `ad_media_view`

## Build Configuration

- **Gradle**: 8.13
- **AGP**: 8.12.3
- **Kotlin**: 2.2.21
- **JDK Target**: Java 11
- **Min SDK**: 26 (Android 8)
- **Target SDK**: 36 (Android 15)

## Distribution

Published to JitPack as `com.github.contabox:ads-mediation`. Version tags follow semantic versioning (v1.0.0, v1.1.0, etc.).

## Important Notes

- Library does NOT include google-services.json - each consuming app provides its own
- Demo app uses Google's test ad unit IDs; production apps must provide real IDs via AdConfig or Remote Config
- `AdmobAppIdOverride.override()` must be called BEFORE `MobileAds.initialize()` to work
- Frequency capping is measured from when ad is DISMISSED, not shown
- ProGuard rules for consuming apps are in `adscore/consumer-rules.pro`
