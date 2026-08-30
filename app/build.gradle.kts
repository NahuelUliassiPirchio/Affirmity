import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Ad secrets are resolved from (in order): a Gradle project property (-P), local.properties (not
// committed, git-ignored), or an environment variable. Debug builds always use Google's committed
// public test ids (see buildConfigField block below) and never consult this resolution.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun adSecret(propKey: String, envKey: String): String? =
    (project.findProperty(propKey) as String?) ?: localProps.getProperty(propKey) ?: System.getenv(envKey)

// Fail-fast is scoped to an actually-requested Release task. Failing unconditionally at
// configuration time would break `assembleDebug` and `testDebugUnitTest` for a developer who has
// no production ad ids -- i.e. for everyone until the AdMob account exists.
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

fun requiredAdSecret(propKey: String, envKey: String): String = adSecret(propKey, envKey)
    ?: if (releaseRequested) {
        error(
            "Missing $propKey in local.properties / -P$propKey / \$$envKey. Release builds must never " +
                "ship Google's public TEST ad units: that serves unpaid ads to real users and is a policy risk."
        )
    } else {
        "MISSING_$envKey"
    }

android {
    namespace = "com.pirxhio.affirmity"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pirxhio.affirmity"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_REWARDED_UNIT_PER_USE", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ONE_TIME_TRIAL", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_TIMED_REPEATABLE", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField(
                "String",
                "ADMOB_TEST_DEVICE_HASH",
                "\"${adSecret("admob.testDeviceHash", "ADMOB_TEST_DEVICE_HASH") ?: ""}\"",
            )
        }
        release {
            optimization {
                enable = true
            }
            manifestPlaceholders["admobAppId"] = requiredAdSecret("admob.appId", "ADMOB_APP_ID")
            buildConfigField(
                "String",
                "ADMOB_REWARDED_UNIT_PER_USE",
                "\"${requiredAdSecret("admob.rewardedUnit.perUse", "ADMOB_REWARDED_UNIT_PER_USE")}\"",
            )
            buildConfigField(
                "String",
                "ADMOB_REWARDED_UNIT_ONE_TIME_TRIAL",
                "\"${requiredAdSecret("admob.rewardedUnit.oneTimeTrial", "ADMOB_REWARDED_UNIT_ONE_TIME_TRIAL")}\"",
            )
            // Deliberately OPTIONAL, unlike the two units above (design D16): a third
            // requiredAdSecret would fail every release build until a new AdMob unit is
            // provisioned. Falls back to the one-time-trial unit id so release builds are never
            // blocked on this specific unit existing.
            buildConfigField(
                "String",
                "ADMOB_REWARDED_UNIT_TIMED_REPEATABLE",
                "\"${
                    adSecret("admob.rewardedUnit.timedRepeatable", "ADMOB_REWARDED_UNIT_TIMED_REPEATABLE")
                        ?: requiredAdSecret("admob.rewardedUnit.oneTimeTrial", "ADMOB_REWARDED_UNIT_ONE_TIME_TRIAL")
                }\"",
            )
            buildConfigField("String", "ADMOB_TEST_DEVICE_HASH", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("es", "en")
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    // Versionless, BOM-managed (matches firebase.auth/firestore/messaging above) -- NOT the
    // deprecated firebase-analytics-ktx, folded into the main artifact since BOM 32.x (D6).
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    testImplementation(libs.junit)
    // Real org.json impl for JVM unit tests only — Android provides org.json at runtime, but the
    // stub android.jar used by testDebugUnitTest throws "not mocked" for its real methods.
    testImplementation(libs.json)
    // Mocks android.content.Context for auth unit tests that only pass it through to a fake
    // AuthProvider — never calls a real platform method.
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // Reflection-based structural tests only (e.g. the analytics no-PII-representable invariant,
    // REQ-6.3.2) — never used by production code.
    testImplementation(kotlin("reflect"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.glance.testing)
    androidTestImplementation(libs.androidx.glance.appwidget.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}