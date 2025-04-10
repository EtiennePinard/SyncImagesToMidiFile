package com.ejrp

import com.ejrp.image.convertToType
import com.ejrp.image.resize
import com.ejrp.midi.*
import com.ejrp.video.Dimensions
import com.ejrp.video.Frame
import com.ejrp.video.HumbleVideo
import com.ejrp.video.HumbleVideoWithAudio
import io.humble.video.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO

/*
If you want to test this project out, run these commands:
cd cli
mvn package -P linux # Or win64 if you are on windows
cd target
java -jar MidiToImgVid-cli-1.0-jar-with-dependencies.jar <midi file path> <audio file path> <images folder path> <format name> <width> <height>
 */

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()
    require(args.size == 6) { arguments }
    // Harcoding it cause Maven moves the lib files anyway
    System.setProperty("java.library.path", "./lib")

    val midiPath = args[0]
    require(isValidFilePath(midiPath))

    val audioPath = args[1]
    val isAudioEncoded = audioPath != "-"
    if (isAudioEncoded) require(isValidFilePath(audioPath))

    val imagePath = args[2]
    require(isValidFolderPath(imagePath))

    val formatName = args[3]
    require(MuxerFormat.getFormats().map { it.name }.contains(formatName)) {
        "The format inputted $formatName is not valid!"
    }

    val width = args[4].toInt()
    val height = args[5].toInt()

    val outputPath = "./output.$formatName"
    createFile(outputPath) // Just to make sure
    println(
        "Creating video from midi file \"$midiPath\"\n" +
                "\twith audio from file \"$audioPath\"" +
                "\tusing images from folder $imagePath\n" +
                "\twith the format $formatName\n" +
                "\twith a width of $width and height of $height"
    )

    val midiFile = FileInputStream(midiPath).parseMidiFile()
    // Only dealing with format 0 or 1 tracks
    require(
        midiFile.headerChunk.format == 0.toUShort() ||
                midiFile.headerChunk.format == 1.toUShort()
    ) {
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
            try {
                ImageIO.read(file)
                return@filter true
            } catch (_: Exception) {
                return@filter false
            }
        }
        .map { ImageIO.read(it).resize(width, height).convertToType(BufferedImage.TYPE_3BYTE_BGR) }
        .shuffled() // Added the shuffling of the list. Will maybe add it as a flag (-s)

    require(images.isNotEmpty()) {
        "There is no images that the ImageIO class can load. Note that the ImageIO class supports " +
                "by default these image file types: bpm, gif, jpeg, png, tiff and wbpm"
    }
    var imageIndex = -1
    val frames = ticksOfNoteOnFromStart.map { midiNote ->
        imageIndex = (imageIndex + 1) % images.size
        Frame(images[imageIndex], midiNote.ticksFromStart.toLong())
    }
    // Note: The last frame is for now an image that last for a very short time
    // It could be a black image, but it would need to be included in the projects ressources
    // or provided by the user
    print(" (${(System.currentTimeMillis() - frameTimeLength) / 1000} seconds)\n")

    val encodingVideoTime = System.currentTimeMillis()
    print("Encoding the images into a video file")

    if (isAudioEncoded) {
        HumbleVideoWithAudio(
            Dimensions(width, height),
            timebaseInSecondPerMidiTicks,
            formatName,
            frames,
            audioPath
        ).writeToFile(outputPath)
    } else {
        HumbleVideo(
            Dimensions(width, height),
            timebaseInSecondPerMidiTicks,
            formatName,
            frames
        ).writeToFile(outputPath)
    }

    print(" (${(System.currentTimeMillis() - encodingVideoTime) / 1000} seconds)\n")
    println("DONE (${(System.currentTimeMillis() - startTime) / 1000} seconds)")
}

const val arguments =
    "Arguments: <midi file path> <audio file path (put `-` if no audio)> <images folder path> <format name> <width> <height>"

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