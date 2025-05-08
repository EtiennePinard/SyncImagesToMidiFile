plugins {
    kotlin("jvm") version "2.1.20"
    `maven-publish`
}

group = "com.ejrp.midiToImgVid"
version = "1.0"


repositories {
    mavenCentral()
    mavenLocal()
}

java {
    withSourcesJar()
}

dependencies {
    implementation("com.ejrp.midi:MidiParser:1.0")

    implementation("org.bytedeco:javacv:1.5.11")
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11")
    implementation("org.bytedeco:ffmpeg-platform:7.1-1.5.11")

    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("MidiToImgVid") {
            artifactId = name
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])

            pom {
                name.set("MidiToImgVid")
                description.set("A library which can aid in the making of a video of still images synced a midi file.")
                url.set("https://github.com/EtiennePinard/SyncImagesToMidiFile")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("ejrp")
                        name.set("Etienne Pinard")
                        email.set("Etienne.Pinard@USherbrooke.ca")
                    }
                }
            }
        }
    }
}

description = "MidiToImgVid-lib"

tasks.test {
    useJUnitPlatform()
}
