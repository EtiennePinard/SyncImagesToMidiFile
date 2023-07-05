# Sync Images To Midi File
This is a program which will create a video of static images
that are synced to a midi file  

### Limitations
The current version will only handle type 0 or 1 midi file    
It can also cannot handle SMPTE time division  

## How to run
#### Requirement: 
- Java
- Maven
- At least one image
- A midi file
- Make sure your output format is installed on your system

1. Open the project in your environment
2. Run   
`mvn package`  
`cd target`  
`java -jar MidiToImgVid-1.0-jar-with-dependencies.jar <midi file path> <images folder path> <format name> <width> <height>`
