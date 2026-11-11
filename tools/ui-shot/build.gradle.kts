// Renders the app's pure-Compose design-system files to PNG on the JVM (no Android SDK needed).
// Run from this folder:  gradle shot -Pout=out
// The app's ui/Tokens.kt, MIcons.kt, Components.kt and Tactile.kt are compiled as-is.
plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}
val cmp = (project.findProperty("cmp") ?: "1.5.12").toString()
dependencies {
    implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$cmp")
    implementation("org.jetbrains.compose.material3:material3-desktop:$cmp")
}
kotlin {
    jvmToolchain(21)
    sourceSets["main"].kotlin.srcDir("build/ui-src")
}
val syncUi = tasks.register<Copy>("syncUi") {
    from("../../app/src/main/java/id/haseda/margaretha/ui") { include("Tokens.kt", "MIcons.kt", "Components.kt", "Tactile.kt") }
    into("build/ui-src")
}
tasks.named("compileKotlin") { dependsOn(syncUi) }
tasks.register<JavaExec>("shot") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ShotKt")
    args = listOf(project.findProperty("out")?.toString() ?: "out")
    jvmArgs("-Djava.awt.headless=true")
}
