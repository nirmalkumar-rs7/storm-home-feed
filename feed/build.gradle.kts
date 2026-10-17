plugins {
    alias(libs.plugins.android.application)
}

// Storm Feed: a companion app that lets the release launcher show Google Discover. The Google app serves its feed only
// to apps it trusts, debuggable ones among them, so this tiny relay is always built debuggable and connects in Storm's
// place. Install feed/build/outputs/apk/release/*.apk.
android {
    namespace = "com.storm.launcher.feed"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.storm.launcher.feed"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // What the Google app looks for; the relay holds nothing worth debugging into
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
