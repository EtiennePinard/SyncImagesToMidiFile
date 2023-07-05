package com.ejrp

import com.ejrp.midi.*
import io.humble.video.*
import io.humble.video.awt.MediaPictureConverterFactory
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO

/*
If you want to test this project out, run these commands:
mvn package
cd target
java -jar MidiToImgVid-1.0-SNAPSHOT-jar-with-dependencies.jar <midi file path> <images folder path> <format name> <width> <height>
 */

@OptIn(ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()
    require(args.size == 5) {
        arguments()
    }
    // Harcoding it cause Maven moves the lib files anyway
    System.setProperty("java.library.path","./lib")
    val midiPath = args[0]
    val imagePath = args[1]
    val formatName = args[2]
    val width = args[3].toInt()
    val height = args[4].toInt()
    require(isValidFilePath(midiPath))
    require(isValidFolderPath(imagePath))
    require(MuxerFormat.getFormats().map { it.name }.contains(formatName)) {
        "The format inputted $formatName is not valid!"
    }
    val outputPath = "./output.$formatName"
    createFile(outputPath) // Just to make sure
    println("Creating video from midi file \"$midiPath\"\n" +
            "using images from folder $imagePath\n" +
            "with the format $formatName\n" +
            "with a width of $width and height of $height")

    val midiFile = FileInputStream(midiPath).parseMidiFile()
    // Only dealing with format 0 or 1 tracks
    require(midiFile.headerChunk.format == 0.toUShort() ||
                    midiFile.headerChunk.format == 1.toUShort()) {
        "The midi file needs to be format 0 or format 1"
    }

    val timebaseStart = System.currentTimeMillis()
    print("Calculating the video timebase from the midi file")
    // Note: timebase is in seconds per midi ticks, so that when the timestamp, which is in midi ticks, is
    // multiplied by the timebase, it gives the correct number of seconds
    // For more info, see: https://stackoverflow.com/a/43337235
    val timebaseInSecondPerMidiTicks = getTimebaseFromFile(midiFile)
    print(" (${System.currentTimeMillis() - timebaseStart} ms)\n")

    val frameTimeLength = System.currentTimeMillis()
    print("Setting the length of each image in the video")
    val ticksOfNoteOnFromStart = getNotesTicksFromStart(midiFile)

    val images = File(imagePath).listFiles()!!
        .filter { file ->
            when (file.extension.lowercase()) {
                "bpm", "gif", "jpeg", "png", "tiff", "wbpm" -> true
                else -> false
            }
        }
        .map { ImageIO.read(it).resize(width, height).convertToType(BufferedImage.TYPE_3BYTE_BGR) }
        .map { it.resize(width, height).convertToType(BufferedImage.TYPE_3BYTE_BGR) }

    var imageIndex = -1
    val frames = ticksOfNoteOnFromStart.map { ticksFromStart: ULong ->
        imageIndex = (imageIndex + 1) % images.size
        Frame(images[imageIndex], ticksFromStart.toLong())
    }
    // Note: The last frame is for now an image that last for a very short time
    // It could be a black image, but it would need to be included in the projects ressources
    // or provided by the user
    print(" (${(System.currentTimeMillis() - frameTimeLength) / 1000} seconds)\n")

    val encodingVideoTime = System.currentTimeMillis()
    print("Encoding the images into a video file")
    val video = Video(
        Dimensions(width, height),
        timebaseInSecondPerMidiTicks,
        formatName,
        frames
    )
    video.writeToFile(outputPath)
    print(" (${(System.currentTimeMillis() - encodingVideoTime) / 1000} seconds)\n")
    println("DONE (${(System.currentTimeMillis() - startTime) / 1000} seconds)")
}

private fun arguments(): String {
    return "Arguments: <midi file path> <images folder path> <format name> <width> <height>"
}

/**
 * Is the file path provided valid
 * @param filePath The path to verify
 * @return True if the file exists, is a file, and it can be read, else false
 */
fun isValidFilePath(filePath: String): Boolean {
    val file = File(filePath)
    return when {
        !file.exists() -> {
            println("The file at the path $filePath does not exist")
            false
        }
        !file.isFile -> {
            println("The file at the $filePath is not a file")
            false
        }
        !file.canRead() -> {
            println("This program does not have read permission for the file at the path $filePath")
            false
        }
        else -> true
    }
}

/**
 * Is the folder path provided valid
 * @param folderPath The path to verify
 * @return True if the folder exists, is a directory and its contents can be read, else false
 */
fun isValidFolderPath(folderPath: String): Boolean {
    val folder = File(folderPath)
    return when {
        !folder.exists() -> {
            println("The folder at the path $folderPath does not exist")
            false
        }
        !folder.isDirectory -> {
            println("The folder at the $folderPath is not a folder")
            false
        }
        !folder.canRead() -> {
            println("This program does not have read permission for the folder at the path $folderPath")
            false
        }
        else -> true
    }
}

private fun createFile(path: String) = File(path).createNewFile()

/**
 * Finds all notes in the midi file, so a note-on and note-off midi event in succession that
 * has the same key. If a note-on event is not followed by its corresponding note-off events, an
 * IllegalArgumentException will be thrown. The ticks of the note-on events from the start of the
 * midi file will be returned in an ordered array
 * @param midiFile The file to extract the notes ticks from
 * @return An ordered array containing the midi ticks of a note from the start of the midi file
 */
@OptIn(ExperimentalUnsignedTypes::class)
private fun getNotesTicksFromStart(midiFile: MidiFile): ULongArray {
    // Only keeping track of note events for now
    val noteEvents = midiFile.midiSequence.tracks
        .flatMap { it.events }
        .filter { it.type == MidiEventType.MIDI_MESSAGE }
        .filter { event ->
            val status = event.statusByte.toUByte().toInt() shr 4
            return@filter status == 0b1000 || status == 0b1001
        }
    // Note: I am not sorting by the ticksFromStart since they should be already sorted in the track
    require(noteEvents.size % 2 == 0) {
        "The needs to be an even number of note events, else some notes won't be have a start or end"
    }
    val ticksOfNoteOnFromStart = ULongArray(noteEvents.size / 2 + 1)
    var i = 0
    while (i < noteEvents.size) {
        val noteOnEvent = noteEvents[i]
        require(noteOnEvent is NoteOnEvent) {
            "The first note event is not a note on event"
        }
        when (val noteOffEvent = noteEvents[i + 1]) {
            is NoteOnEvent -> {
                require(noteOffEvent.key == noteOnEvent.key) {
                    "The next note event key does not match the previous note on event key"
                }
                require(noteOffEvent.velocity == (0.toByte())) {
                    "The next event after the note on event is another note event with a velocity not equal to 0\n," +
                            "which would mean that two notes will be played at the same time, which not allowed"
                }
                ticksOfNoteOnFromStart[i / 2] = noteOnEvent.tickFromStart
                if (i + 1 == noteEvents.lastIndex) {
                    ticksOfNoteOnFromStart[i / 2 + 1] = noteOffEvent.tickFromStart
                }
            }

            is NoteOffEvent -> {
                require(noteOffEvent.key == noteOnEvent.key) {
                    "The next note event key does not match the previous note on event key"
                }
                ticksOfNoteOnFromStart[i / 2] = noteOnEvent.tickFromStart
                if (i + 1 == noteEvents.lastIndex) {
                    ticksOfNoteOnFromStart[i / 2 + 1] = noteOffEvent.tickFromStart
                }
            }
        }
        i += 2
    }
    return ticksOfNoteOnFromStart
}

/**
 * Get the timebase of a video in seconds per midi ticks from a midi file.
 * The function will first try to find any tempo events in the file. If there
 * are more than one tempo event, an IllegalArgumentException will be thrown.
 * If the division of this midi file is not ticks per quarter note, an IllegalArgumentException
 * will be thrown. If there is no tempo event, the tempo will be assumed to be 120 quarter notes per minutes
 * @param midiFile The file to get the timebase from
 * @return The timebase of this midi file in seconds per midi ticks
 */
private fun getTimebaseFromFile(midiFile: MidiFile): Rational {
    // Trying to find the tempo event, if it is set
    val tempoEvent = midiFile.midiSequence.tracks
        .flatMap { it.events }
        .filter { it.type == MidiEventType.META_EVENT }
        .filter { event ->
            event.data[0] == 0xFF.toByte() && event.data[1] == 0x51.toByte() && event.data[2] == 0x03.toByte()
        }
    require(tempoEvent.size <= 1) {
        "This program only supports a constant tempo, and there is more than one tempo event in the file"
    }
    val division = midiFile.headerChunk.division
    require(division is TicksPerQuarterNote) {
        "This program currently supports the ticks per quarter note midi division, not SMPTE division"
    }
    val ticksPerQuarterNote = division.tickPerQuarterNote.toInt() // Where a quarter note is 1 beat
    return if (tempoEvent.isEmpty()) {
        val defaultTempo = 120
        val secondsPerMinute = 60
        Rational.make(
            secondsPerMinute,
            defaultTempo * ticksPerQuarterNote
        )
    } else {
        val microSecondsPerQuarterNote = byteArrayOf(0).plus(
            tempoEvent[0].data.subArray(3, tempoEvent[0].data.size)).getU32FromBytes().toInt()
        val microSecondsPerSeconds = 1_000_000
        Rational.make(
            microSecondsPerQuarterNote,
            microSecondsPerSeconds * ticksPerQuarterNote
        )
    }
}

/**
 * Represents a frame of video. Make sure that every image of every frame have the same dimensions
 * @property image The image that will be displayed for that frame
 * @property timestamp The value that will be multiplied by the timebase to get the time that
 * this frame will occur from the start of the video
 * @constructor Creates a Frame object
 */
data class Frame(val image: BufferedImage, val timestamp: Long)

/**
 * Represents the dimension of a video
 * @property width The width of a video
 * @property height The height of a video
 * @constructor Create a Dimensions object
 */
data class Dimensions(val width: Int, val height: Int)

/**
 * Represents a video
 * @property dimensions The width and the height of the video
 * @property timebase The timebase of the video. It is a value that will be
 * multiplied by the timestamp of each frame to determine the frames length in seconds
 * @property formatName The name of the video file format of this video. Ex: "mp4"
 * @property frames A list of frames that contain the image of a frame and its timestamp
 * @constructor Creates a Video object
 */
class Video(
    private val dimensions: Dimensions,
    private val timebase: Rational,
    private val formatName: String,
    private val frames: List<Frame>
) {

    init {
        frames.forEach { frame ->
            require(frame.image.width == dimensions.width) {
                "The width ${frame.image.width} of the image does not equal the width of the video ${dimensions.width}"
            }
            require(frame.image.height == dimensions.height) {
                "The height ${frame.image.height} of the image does not equal the height of the video ${dimensions.height}"
            }
            require(frame.image.type == BufferedImage.TYPE_3BYTE_BGR) {
                "The type ${frame.image.type} of this image is not TYPE_3BYTE_BGR (${BufferedImage.TYPE_3BYTE_BGR})"
            }
        }
    }

    fun writeToFile(outputPath: String) {
        // This function is a derivation of this example:
        // https://github.com/artclarke/humble-video/blob/master/humble-video-demos/src/main/java/io/humble/video/demos/RecordAndEncodeVideo.java
        val muxer = Muxer.make(outputPath, null, formatName)
        // Getting the codec
        // Please note that we do not support audio yet nor do we support choosing your own codec, sorry
        val codec = Codec.findEncodingCodec(muxer.format.defaultVideoCodecId)
        val encoder = Encoder.make(codec)
        encoder.width = dimensions.width
        encoder.height = dimensions.height
        // Most video format uses yuv420p pixel format
        val pixelFormat = PixelFormat.Type.PIX_FMT_YUV420P
        encoder.pixelFormat = pixelFormat
        encoder.timeBase = timebase
        // Does the format need a global header?
        if (muxer.format.getFlag(ContainerFormat.Flag.GLOBAL_HEADER)) {
            encoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true)
        }
        // Opening the encoder and muxer
        encoder.open(null, null)
        muxer.addNewStream(encoder)
        muxer.open(null, null)

        val picture = MediaPicture.make(
            encoder.width,
            encoder.height,
            pixelFormat
        )
        picture.timeBase = encoder.timeBase

        val packet = MediaPacket.make()
        val converter = MediaPictureConverterFactory.createConverter(frames[0].image, picture)
        frames.forEach { frame ->
            // We need to convert a rgb color space to a Y'CbCr color space
            converter.toPicture(picture, frame.image, frame.timestamp)
            do {
                encoder.encode(packet, picture)
                if (packet.isComplete) {
                    muxer.write(packet, false)
                }
            } while (packet.isComplete)
        }

        // Flushing any cache in the encoder
        do {
            encoder.encode(packet, null)
            if (packet.isComplete)
                muxer.write(packet,  false)
        } while (packet.isComplete)

        muxer.close()
    }
}

/**
 * Converts the buffered image to a specific type
 * @param targetType The type that the buffered needs to be converted to
 * @return A new buffered image of the appropriate type or the same buffered image if it already was the target type
 */
fun BufferedImage.convertToType(targetType: Int): BufferedImage {
    if (this.type == targetType) { return this }
    val image = BufferedImage(this.width, this.height, targetType)
    val graphics = image.createGraphics()
    graphics.drawImage(this, 0, 0, null)
    graphics.dispose()
    return image
}

/**
 * Resizes a buffered image to a specific width and height
 * @param newWidth The width to resize the buffered image to
 * @param newHeight The height to resize the buffered image to
 * @return A new buffered image with the appropriate width and height or the same buffered image if it already was the new size
 */
fun BufferedImage.resize(newWidth: Int, newHeight: Int): BufferedImage {
    if (this.width == newWidth && this.height == newHeight) { return this }
    val tmp = this.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
    val dimg = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
    val g2d = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    return dimg
}