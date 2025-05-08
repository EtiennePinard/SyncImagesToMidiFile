# Sync Images To Midi File

This is a program which will create a video of static images which are synced to a midi file.

### Limitations

The current version will only handle type 0 or 1 midi file    
It also cannot handle SMPTE time division

## Using the library

- Clone the repo
- Run the example program with `./gradlew :MidiToImgVid-cli:runExample`.
  If everything works correctly you should see the file example.mp4 in the cli folder appear.
  Open the video file to see an example of the power of this library.
- If you want to include the library in your project, the easiest way is to put the library jar file in your local maven
  repository using the command
```bash
./gradlew :MidiToImgVid-lib:publishToMavenLocal
```
Next you can easily include the project in gradle with
```kotlin
repositories {
    mavenLocal()
}

dependencies { 
    implementation("com.ejrp.midiToImgVid:MidiToImgVid:1.0")
}
```
or in maven with
```xml
<dependency>
    <groupId>com.ejrp.midiToImgVid</groupId>
    <artifactId>MidiToImgVid</artifactId>
    <version>1.0</version>
</dependency>
```

## Disclaimer

I do not own the music and midi file used in the example program.
- The music is At the Speed of Light by Dimrain47: https://www.newgrounds.com/audio/listen/467339
- The midi file is a transcription by NoTwo of At the Speed of Light: https://youtu.be/7D5XGnYbJak?si=FsgCzX1P60Mr-laa
