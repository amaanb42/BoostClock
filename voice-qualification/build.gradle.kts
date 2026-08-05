plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":voice-engine-api"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("org.fossify.clock.voice.qualification.MainKt")
}
