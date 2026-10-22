plugins {
    id("com.android.application")
}

android {
    namespace = "br.arthur.treino"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.arthur.treino"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "1")
    }

    // Chave fixa (vem dos segredos do GitHub) para as atualizações instalarem por cima
    val chave = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        create("app") {
            if (chave != null) {
                storeFile = file(chave)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = "treino"
                keyPassword = System.getenv("KEYSTORE_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (chave != null) signingConfigs.getByName("app") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
