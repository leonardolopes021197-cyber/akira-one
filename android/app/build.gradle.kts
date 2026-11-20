plugins {
    id("com.android.application")
}

android {
    namespace = "com.akira.one"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.akira.one"
        minSdk = 23
        targetSdk = 35
        versionCode = 35
        versionName = "1.12.3"
    }

    // Chave de assinatura FIXA do AKIRA ONE (android/akira-signing.jks).
    // O Android só atualiza um app por cima se a assinatura for a mesma, então TODA versão
    // (compilada no GitHub ou no celular) precisa sair com esta chave. Não perca este arquivo.
    val akiraKeystore = file("../akira-signing.jks")
    signingConfigs {
        if (akiraKeystore.exists()) {
            create("akira") {
                storeFile = akiraKeystore
                storeType = "pkcs12"
                storePassword = "IFuAsJmrjzsLDbqmORcTFQ9u"
                keyAlias = "akira"
                keyPassword = "IFuAsJmrjzsLDbqmORcTFQ9u"
            }
        }
    }

    buildTypes {
        debug {
            if (akiraKeystore.exists()) signingConfig = signingConfigs.getByName("akira")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (akiraKeystore.exists()) signingConfig = signingConfigs.getByName("akira")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
