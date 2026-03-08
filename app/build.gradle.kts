import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.sharedkhatm"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val props = Properties()
            val propFile = rootProject.file("keystore.properties")
            if (propFile.canRead()) {
                propFile.reader(Charsets.UTF_8).use { props.load(it) }
            }
            val storeFileStr = props["RELEASE_STORE_FILE"]?.toString()
            if (!storeFileStr.isNullOrBlank()) {
                val keyStoreFile = rootProject.file(storeFileStr)
                if (keyStoreFile.exists()) {
                    storeFile = keyStoreFile
                    storePassword = props["RELEASE_STORE_PASSWORD"]?.toString() ?: ""
                    keyAlias = props["RELEASE_KEY_ALIAS"]?.toString() ?: ""
                    keyPassword = props["RELEASE_KEY_PASSWORD"]?.toString() ?: ""
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.hatimhalkasi.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 27
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null && releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    // bazı projelerde Compose derleyici uyumu için iyi olur
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

// Production: Sadece Banner + Rewarded aktif; App Open ve Interstitial release'te kapalı.
val TEST_PUBLISHER_ID = "3940256099942544"
tasks.register("checkReleaseAdIds") {
    doLast {
        // 0) Main kaynak kodunda test ID olmamalı (hardcoded)
        file("src/main").walkTopDown()
            .filter { it.extension in listOf("kt", "java", "xml") }
            .forEach { f ->
                if (f.readText().contains(TEST_PUBLISHER_ID)) {
                    throw GradleException(
                        "RELEASE BUILD HATASI: $f içinde Google test reklam ID'si bulundu. Kaldırın veya sadece debug kaynakta kullanın."
                    )
                }
            }
        val releaseStrings = file("src/release/res/values/strings.xml")
        if (!releaseStrings.exists()) {
            throw GradleException("src/release/res/values/strings.xml bulunamadı. Release reklam ID'leri tanımlanmalı.")
        }
        val content = releaseStrings.readText()
        // 1) Test yayıncı ID release kaynaklarında OLMAMALI
        if (TEST_PUBLISHER_ID in content) {
            throw GradleException(
                "RELEASE BUILD HATASI: Google test reklam yayıncı ID'si release kaynaklarında bulundu. " +
                "Production'da yalnızca gerçek AdMob birim ID'leri kullanılmalı."
            )
        }
        // 2) Production'da aktif olan reklamlar: sadece Banner + Rewarded zorunlu
        val requiredIds = listOf(
            "admob_banner_id",
            "admob_rewarded_id"
        )
        for (id in requiredIds) {
            val regex = Regex("""<string name="$id">([^<]*)</string>""")
            val match = regex.find(content)
            val value = match?.groupValues?.get(1)?.trim().orEmpty()
            if (value.isEmpty() || value.contains("PLACEHOLDER", ignoreCase = true) || value.contains("REPLACE", ignoreCase = true)) {
                throw GradleException(
                    "RELEASE BUILD HATASI: $id boş veya placeholder. AdMob konsolundan gerçek birim ID girin."
                )
            }
            if (!value.matches(Regex("""ca-app-pub-\d+/\d+"""))) {
                throw GradleException(
                    "RELEASE BUILD HATASI: $id geçersiz format. Örnek: ca-app-pub-XXXXXXXXXXXXXXX/YYYYYYYYYY"
                )
            }
        }
    }
}

gradle.projectsEvaluated {
    tasks.findByName("mergeReleaseResources")?.dependsOn("checkReleaseAdIds")
    tasks.findByName("bundleRelease")?.dependsOn("checkReleaseAdIds")
    tasks.findByName("assembleRelease")?.dependsOn("checkReleaseAdIds")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    // --- TEMEL ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // --- NAVIGATION ---
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.4")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.4")

    // --- RETROFIT ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // --- FIREBASE (BoM) ---
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // ✅ Remote Config (tek satır yeterli)
    implementation("com.google.firebase:firebase-config-ktx:22.1.2")

    // ✅ Firebase AI
    implementation("com.google.firebase:firebase-ai")

    // --- KONUM ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- ADMOB (lazy init, main thread block yok) ---
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // --- DATASTORE (24h reklamsız timestamp) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- DİĞER ---
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.intuit.sdp:sdp-android:1.1.0")
    implementation("com.intuit.ssp:ssp-android:1.1.0")
    implementation("androidx.browser:browser:1.8.0")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")

    // --- COMPOSE ---
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")

    // LeakCanary (sadece debug - memory leak tespiti)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // --- TEST ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.android.exoplayer:exoplayer:2.19.1")

    // Lottie (dark/light mode toggle — assets/Switch.json)
    implementation("com.airbnb.android:lottie:5.2.0")
}
