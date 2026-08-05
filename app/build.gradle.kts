import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = project.property("VERSION_CODE").toString().toInt()
        setProperty("archivesBaseName", "clock-$versionCode")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
        noCompress += listOf("onnx", "ort")
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        jniLibs {
            // Keep arm64 for devices and x86_64 for prototype testing on Android emulators.
            listOf("armeabi-v7a", "x86").forEach { abi ->
                excludes += "**/$abi/libonnxruntime.so"
                excludes += "**/$abi/libsherpa-onnx-c-api.so"
                excludes += "**/$abi/libsherpa-onnx-cxx-api.so"
                excludes += "**/$abi/libsherpa-onnx-jni.so"
            }
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    implementation(project(":voice-engine-api"))
    implementation(files("libs/sherpa-onnx-1.13.4.aar"))
    implementation(libs.fossify.commons)

    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.numberpicker)
    implementation(libs.autofittextview)
    implementation(libs.eventbus)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
    testImplementation(libs.junit4)
}

val voiceArtifactHashes = mapOf(
    "libs/sherpa-onnx-1.13.4.aar" to
        "03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780",
    "src/main/assets/voice/sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27/encoder_model.ort" to
        "94e90a4654fc45cdfedb77c4c08e1739f48862998e58fada384b25118134f221",
    "src/main/assets/voice/sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27/decoder_model_merged.ort" to
        "cf524c4862d36e9e5ab032eddc73637efd822d70e868ac575cf1a46e1e4708a0",
    "src/main/assets/voice/sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27/tokens.txt" to
        "2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049",
    "src/main/assets/voice/sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27/LICENSE" to
        "6148d7574a6554b7379b633cfd4c4fe5840c3f548d13bc83e00b52dc6fa00abd",
    "src/main/assets/voice/LICENSE.sherpa-onnx.txt" to
        "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
)

val verifyVoiceArtifacts by tasks.registering {
    inputs.files(voiceArtifactHashes.keys.map(::file))
    doLast {
        voiceArtifactHashes.forEach { (path, expected) ->
            val artifact = file(path)
            check(artifact.isFile) { "Missing voice artifact: $path" }
            val digest = MessageDigest.getInstance("SHA-256")
            artifact.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == expected) { "Voice artifact checksum mismatch: $path" }
        }
    }
}

tasks.named("preBuild").configure { dependsOn(verifyVoiceArtifacts) }
