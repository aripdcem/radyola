plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Ortam değişkeni ya da Gradle özelliğinden gizli değer okur.
 *
 * Boş dize yok sayılır: CI'da tanımsız bir secret ortama boş dize olarak
 * geçiyor ve `?:` zincirini tetiklemiyor — alias'ı boş bir imza yapılandırması
 * derlemeyi anlaşılmaz bir hatayla düşürür.
 */
fun secretOf(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }

/**
 * Sürüm numaraları dışarıdan verilebilir.
 *
 * CI her derlemede artan bir `versionCode` geçirir; aksi hâlde her yapı aynı
 * numarayı taşır ve cihaz yeni APK'yı güncelleme saymaz. Elle derlemede
 * aşağıdaki varsayılanlar kullanılır.
 */
val buildVersionCode = secretOf("RADYOLA_VERSION_CODE", "radyola.versionCode")?.toIntOrNull() ?: 1
val buildVersionName = secretOf("RADYOLA_VERSION_NAME", "radyola.versionName") ?: "1.0.0"

/**
 * Yayın imzası için anahtar deposu.
 *
 * Yol ve parolalar ortam değişkeninden (CI: GitHub secrets) ya da yerel
 * `gradle.properties`'ten okunur. Parolalar bilerek `-P` ile değil ortam
 * değişkeniyle taşınır: `-P` süreç listesinde ve derleme günlüğünde görünebilir.
 * Anahtar deposu yoksa debug anahtarına düşülür — imzasız APK yan yüklenemez.
 */
val releaseKeystore = secretOf("RADYOLA_KEYSTORE_FILE", "radyola.keystoreFile")
    ?.let { path -> file(path) }
    ?.takeIf { it.isFile }

android {
    namespace = "com.aripd.radyola"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aripd.radyola"
        minSdk = 24
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = secretOf("RADYOLA_KEYSTORE_PASSWORD", "radyola.keystorePassword")
                keyAlias = secretOf("RADYOLA_KEY_ALIAS", "radyola.keyAlias") ?: "radyola"
                keyPassword = secretOf("RADYOLA_KEY_PASSWORD", "radyola.keyPassword")
                    ?: secretOf("RADYOLA_KEYSTORE_PASSWORD", "radyola.keystorePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Kendi anahtarımız varsa onunla; yoksa debug anahtarıyla imzala —
            // imzasız APK yan yüklenemez.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.datastore.preferences)

    testImplementation("junit:junit:4.13.2")
    // android.jar'daki org.json saplamaları çağrılınca fırlatıyor; JSON
    // ayrıştırmasını birim testinde çalıştırabilmek için gerçek uygulama.
    testImplementation("org.json:json:20240303")
}
