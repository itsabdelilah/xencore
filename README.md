# AdMob Mediation Library

Professional Android library for AdMob mediation with Firebase Remote Config integration, runtime App ID override, and frequency capping.

## ✨ Features

- ✅ **AdMob + Meta + AppLovin mediation** with automatic bidding
- ✅ **Firebase Remote Config** for remote ad unit IDs and frequency control
- ✅ **Runtime APPLICATION_ID override** - Switch AdMob accounts without app updates
- ✅ **Frequency capping** for Interstitial and App Open ads (configurable remotely)
- ✅ **Custom native ad layouts** per app with flexible styling
- ✅ **Shimmer loading effects** for professional UX
- ✅ **Comprehensive logging** for debugging mediation waterfall
- ✅ **UMP consent management** built-in
- ✅ **One-line ad calls** with simple callbacks

## 📦 Installation

### Step 1: Add JitPack Repository

In your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add Dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.contabox:ads-mediation:1.0.0")
}
```

### Step 3: Add Google Services Plugin

In your app's `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")  // Add this
}
```

## 🔥 Firebase Setup (CRITICAL)

### Understanding the Architecture

**Library Structure:**
- ✅ The library **includes** Firebase Remote Config **code**
- ✅ The library **exposes** Firebase SDK dependency (via `api`)
- ❌ The library **does NOT include** `google-services.json`
- ❌ The library **does NOT have** a Firebase project

**Each App Provides:**
- ✅ Its own `google-services.json` file
- ✅ Its own Firebase project
- ✅ Its own Remote Config parameters
- ✅ google-services plugin (already shown in Step 3 above)

### How It Works at Runtime

When your app calls `FirebaseRemoteConfig.getInstance()`, it automatically connects to **your app's Firebase project** (configured via your `google-services.json`).

**Example:**
```
FFH Quatro App:
  ├─ app/google-services.json (FFH's Firebase project ID)
  ├─ FirebaseApp.initializeInit() → connects to FFH's project
  ├─ Library's RemoteConfigManager.init() is called
  └─ FirebaseRemoteConfig.getInstance() → uses FFH's project automatically!
```

**Multi-App Scenario:**
- **FFH Quatro** uses FFH's `google-services.json` → gets FFH's Remote Config values
- **Sensi Max** uses Sensi's `google-services.json` → gets Sensi's Remote Config values
- **Same library code** works for both apps with different configurations!

### Setup Steps for Your App

#### 1. Create Your Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" (e.g., "MyApp-Firebase")
3. Add your Android app with your package name
4. Download `google-services.json`

#### 2. Add google-services.json to Your App

**⚠️ IMPORTANT:** Place it in your `app/` module, **NOT** in the library!

```
YourProject/
├── app/
│   ├── google-services.json    ← Place it HERE!
│   └── build.gradle.kts
└── adscore/                     ← NOT here!
```

#### 3. Configure Remote Config Parameters

In your Firebase Console → Remote Config, add these parameters:

| Parameter | Type | Example Value |
|-----------|------|---------------|
| `admob_app_id` | String | `ca-app-pub-XXXXX~YYYYY` |
| `open_ad_unit` | String | `ca-app-pub-XXXXX/1111111111` |
| `inter_ad_unit` | String | `ca-app-pub-XXXXX/2222222222` |
| `reward_ad_unit` | String | `ca-app-pub-XXXXX/3333333333` |
| `native_ad_unit` | String | `ca-app-pub-XXXXX/4444444444` |
| `ad_frequency_seconds` | Number | `30` |

Or import this template:

```json
{
  "parameters": {
    "admob_app_id": {
      "defaultValue": {
        "value": "ca-app-pub-XXXXX~YYYYY"
      }
    },
    "open_ad_unit": {
      "defaultValue": {
        "value": "ca-app-pub-XXXXX/1111111111"
      }
    },
    "inter_ad_unit": {
      "defaultValue": {
        "value": "ca-app-pub-XXXXX/2222222222"
      }
    },
    "reward_ad_unit": {
      "defaultValue": {
        "value": "ca-app-pub-XXXXX/3333333333"
      }
    },
    "native_ad_unit": {
      "defaultValue": {
        "value": "ca-app-pub-XXXXX/4444444444"
      }
    },
    "ad_frequency_seconds": {
      "defaultValue": {
        "value": "30"
      }
    }
  }
}
```

### Result

✅ Each of your apps uses its own Firebase project
✅ Each app has different Remote Config values
✅ Library code is shared, configuration is per-app
✅ Update Remote Config without app updates!

---

## 🚀 Quick Start

### 1. Setup Firebase

1. Add your `google-services.json` to your app module
2. Configure Remote Config parameters (see below)

### 2. Add AdMob App ID to AndroidManifest

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
</application>
```

### 3. Initialize in Application Class

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize AdsManager
        AdsManager.init(this)
    }
}
```

### 4. Request Consent in MainActivity

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Request consent and initialize SDK
    AdsManager.requestConsentAndInitialize(this)

    // Preload ads
    AdsManager.preloadAppOpen()
    AdsManager.preloadInterstitial()
    AdsManager.preloadRewarded()
}
```

## 📱 Show Ads

### App Open Ad

```kotlin
AdsManager.showAppOpen(
    activity = this,
    onComplete = { /* Ad dismissed */ },
    onFail = { /* Ad failed or blocked by frequency */ }
)
```

### Interstitial Ad

```kotlin
AdsManager.showInterstitial(
    activity = this,
    onComplete = { /* Ad dismissed */ },
    onFail = { /* Ad failed or blocked by frequency */ }
)
```

### Rewarded Ad

```kotlin
AdsManager.showRewarded(
    activity = this,
    onRewarded = { amount, type ->
        // Give reward to user
        Log.d(TAG, "User earned: $amount $type")
    },
    onComplete = { /* Ad dismissed */ },
    onFail = { /* Ad failed or user dismissed */ }
)
```

### Native Ad

```kotlin
// Create a container in your layout
<FrameLayout
    android:id="@+id/nativeAdContainer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />

// Load native ad
AdsManager.loadNative(
    container = nativeAdContainer,
    layoutId = R.layout.my_native_ad_layout,
    hideEmptyFields = true,
    onLoaded = { nativeAd, container ->
        // Ad loaded and displayed
        container.visibility = View.VISIBLE
    },
    onFail = { /* Ad failed */ }
)
```

## 🎨 Custom Native Ad Layouts

Create your own layout with **required view IDs**:

```xml
<com.google.android.gms.ads.nativead.NativeAdView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <!-- REQUIRED IDs -->
        <TextView
            android:id="@+id/ad_headline"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/ad_body"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <Button
            android:id="@+id/ad_call_to_action"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <!-- OPTIONAL IDs -->
        <ImageView
            android:id="@+id/ad_app_icon"
            android:layout_width="48dp"
            android:layout_height="48dp" />

        <RatingBar
            android:id="@+id/ad_stars"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/ad_advertiser"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
    </LinearLayout>
</com.google.android.gms.ads.nativead.NativeAdView>
```

**Style with your app's colors, fonts, and shapes!**

## ⚙️ Firebase Remote Config Parameters

Configure these parameters in Firebase Console:

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `admob_app_id` | String | Override App ID at runtime | "" (use manifest) |
| `open_ad_unit` | String | App Open ad unit ID | Test ID |
| `inter_ad_unit` | String | Interstitial ad unit ID | Test ID |
| `reward_ad_unit` | String | Rewarded ad unit ID | Test ID |
| `native_ad_unit` | String | Native ad unit ID | Test ID |
| `ad_frequency_seconds` | Number | Seconds between interstitial/app open ads | 30 |

### Example Configuration:

```json
{
  "admob_app_id": "ca-app-pub-XXXXX~YYYYY",
  "open_ad_unit": "ca-app-pub-XXXXX/1111111111",
  "inter_ad_unit": "ca-app-pub-XXXXX/2222222222",
  "reward_ad_unit": "ca-app-pub-XXXXX/3333333333",
  "native_ad_unit": "ca-app-pub-XXXXX/4444444444",
  "ad_frequency_seconds": 30
}
```

## 🔧 Runtime App ID Override

The library can override your AdMob App ID at runtime via Remote Config:

1. Set `admob_app_id` in Firebase Remote Config
2. The library fetches it on app start
3. App ID is injected **before** `MobileAds.initialize()`
4. Switch AdMob accounts without app updates!

**Use cases:**
- Switch to a different AdMob account after publishing
- Emergency fixes without Play Store review
- A/B test different ad accounts

## ⏱️ Frequency Capping

Automatically prevents ad spam:

- **Interstitial ads**: Minimum time between shows (default: 30s)
- **App Open ads**: Minimum time between shows (default: 30s)
- **Rewarded ads**: No frequency cap (user-initiated)
- **Native ads**: No frequency cap (static display)

**Configure remotely** via `ad_frequency_seconds` parameter!

## 📊 Mediation Logging

The library logs detailed mediation information:

```
✅ INTERSTITIAL AD LOADED BY: 🔵 APPLOVIN
   ├─ Adapter: com.google.ads.mediation.applovin.AppLovinAdapter
   ├─ Response ID: abc123
   └─ Latency: 234ms
📊 Mediation Waterfall (3 adapters):
   1. AppLovinAdapter → ✅ Success (234 ms)
   2. FacebookAdapter → ❌ No fill (156 ms)
   3. AdMobAdapter → ❌ Timeout
```

## 🛡️ ProGuard

ProGuard rules are automatically applied via `consumer-rules.pro`.

No additional configuration needed!

## 📝 License

MIT License

## 🤝 Contributing

Contributions welcome! Please open an issue or PR.

## 📧 Support

For issues, please visit: [GitHub Issues](https://github.com/contabox/ads-library/issues)

---

Made with ❤️ for Android developers
