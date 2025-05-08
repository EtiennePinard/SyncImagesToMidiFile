plugins {
    kotlin("jvm") version "2.1.20"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":MidiToImgVid-lib"))
    implementation("com.ejrp.midi:MidiParser:1.0")
}

tasks.register<JavaExec>("runExample") {
    group = "application"
    description = "Runs the example program, which will create a video in ./cli/example.mp4"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ejrp.ExampleKt")
}

tasks.register<JavaExec>("runCli") {
    group = "application"
    description =
        "Runs the cli program, which is will prompt you for your own images, midi file and audio file and then " +
                "assemble everything in a video with the image synced to the midi file"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ejrp.CliKt")
}

tasks.register<JavaExec>("runMidiVerifier") {
    group = "application"
    description =
        "Runs the midi verifier program, which is will give you an analysis on your midi file"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ejrp.MidiVerifierKt")

    // Command line arguments via -PrunArgs="..."
    val runArgs = project.findProperty("runArgs") as String?
    if (!runArgs.isNullOrBlank()) {
        args = runArgs.split(" ")
    }
}

description = "MidiToImgVid-cli"
