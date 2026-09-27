plugins {
    alias(libs.plugins.android.application)
}

// La variante sin google-services.json sigue compilando para revisión local. Al agregar
// el archivo descargado desde Firebase, Gradle genera las opciones reales de FCM.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// La clave de carga es propiedad del publicador. Nunca se guarda en Git ni se
// sustituye por la clave de depuración al generar un paquete para Play.
val uploadStoreFile = providers.environmentVariable("FALTOS_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("FALTOS_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("FALTOS_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("FALTOS_UPLOAD_KEY_PASSWORD").orNull
val uploadValues = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
require(uploadValues.all { it.isNullOrBlank() } || uploadValues.all { !it.isNullOrBlank() }) {
    "Configure las cuatro variables FALTOS_UPLOAD_* para firmar el paquete de Play."
}
val uploadConfigured = uploadValues.all { !it.isNullOrBlank() }

// El filtro del manifiesto y el esquema que comprueba el código salen del mismo valor.
fun com.android.build.api.dsl.VariantDimension.esquemaDeAcceso(esquema: String) {
    manifestPlaceholders["oauthRedirectScheme"] = esquema
    resValue("string", "oauth_esquema", esquema)
}

android {
    namespace = "com.hidalgoferrai.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Identificador definitivo: después de publicar en Play ya no se puede cambiar.
        applicationId = "com.hidalgoferrai.faltos"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"
        // Un solo sitio define el esquema de vuelta del acceso: el filtro del manifiesto y el
        // que comprueba LoginActivity salen de aquí, así nunca se separan.
        esquemaDeAcceso("faltas")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Necesario para generar el texto del esquema de acceso desde este archivo.
    buildFeatures {
        resValues = true
    }

    signingConfigs {
        if (uploadConfigured) create("playUpload") {
            storeFile = file(uploadStoreFile!!)
            storePassword = uploadStorePassword
            keyAlias = uploadKeyAlias
            keyPassword = uploadKeyPassword
        }
    }
    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            // La instalación de pruebas no debe competir por el callback de la app normal.
            esquemaDeAcceso("faltas-qa")
            matchingFallbacks += listOf("debug")
        }
        release {
            if (uploadConfigured) signingConfig = signingConfigs.getByName("playUpload")
            // Con la optimización activada R8 quita el código y los recursos que no se usan:
            // el paquete baja de ~11,8 MB de código a una fracción. Las reglas de conservación
            // están en src/main/keepRules; el resto de bibliotecas trae las suyas.
            optimization {
                enable = true
            }
        }
    }
    testBuildType = "qa"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.webkit)
    implementation(libs.core.splashscreen)
    implementation(libs.document.scanner)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
