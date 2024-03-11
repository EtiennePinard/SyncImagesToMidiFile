# Sync Images To Midi File
This is a program which will create a video of static images
which are synced to a midi file. There is also an option to add audio to the video

### Limitations
The current version will only handle type 0 or 1 midi file    
It also cannot handle SMPTE time division  

## How to use the library
1. Clone the repo
2. Run `mvn install`
3. Include the dependency in your Maven project
```xml
<dependency>
    <groupId>com.ejrp</groupId>
    <artifactId>MidiToImgVid-lib</artifactId>
    <version>1.0</version>
</dependency>
```

## How to use the cli
#### Requirement: 
- Java
- Maven
- At least one image (if you are using multiple images, make sure they are all the same dimensions for better results)
- A midi file
- Make sure your output format is installed on your system
- Audio file (optional)  

1. Clone the repo
2. Run   
`cd cli`
`mvn package -P linux` Note: Use `win64` if you are on windows     
`cd target`  
`java -jar MidiToImgVid-cli-1.0-jar-with-dependencies.jar <midi file path> <audio file path (put "-" if no audio)> <images folder path> <format name> <width> <height>`  

Note: If you want to move the jar file, make sure to also move the lib folder so that the jar file
and the lib folder exists in the same directory. The lib folder contains the native library
needed to run humble, the media processing library that uses the JNI
