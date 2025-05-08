package com.ejrp

import com.ejrp.midiToImgVid.image.convertToType
import com.ejrp.midiToImgVid.image.getBlackBufferedImage
import com.ejrp.midiToImgVid.image.letterboxToSize
import com.ejrp.midi.*
import com.ejrp.midiToImgVid.midi.getNotesTicksFromStart
import com.ejrp.midiToImgVid.midi.getTimebaseFromFile
import com.ejrp.midiToImgVid.video.Dimensions
import com.ejrp.midiToImgVid.video.JavaCVFrame
import com.ejrp.midiToImgVid.video.JavaCVVideoWithAudio
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()
    require(args.size == 6) { arguments }

    val midiPath = args[0]
    require(File(midiPath).isValidFile())

    val audioPath = args[1]
    require(File(audioPath).isValidFile()) {
        "You need to provide a valid audio file!"
    }

    val imagePath = args[2]
    require(File(imagePath).isValidFolder())

    val formatName = args[3]

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

    val midiFile = StandardMidiFile.fromInputStream(FileInputStream(midiPath))
    // Only dealing with format 0 tracks
    require(midiFile.headerChunk.format == 0u.toUShort() || midiFile.headerChunk.format == 1u.toUShort()) {
        "Only format 0 or format 1 midi files are allowed"
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

    val workDirectory = File("$imagePath/img_sync_work_folder/")
    val images = File(imagePath).listFiles()!!
        .filter { file ->
            try {
                ImageIO.read(file)
                return@filter true
            } catch (_: Exception) {
                return@filter false
            }
        }
        .map {
            val image = ImageIO.read(it).letterboxToSize(width, height).convertToType(BufferedImage.TYPE_3BYTE_BGR)
            val newFile = File("$workDirectory/${it.name}")
            newFile.createNewFile()
            ImageIO.write(image, it.extension, newFile)
            image.flush()
            return@map newFile.path
        }
        .shuffled() // Added the shuffling of the list. Will maybe add it as a flag (-s)

    require(images.isNotEmpty()) {
        "There is no images that the ImageIO class can load. Note that the ImageIO class supports " +
                "by default these image file types: bpm, gif, jpeg, png, tiff and wbpm"
    }
    var imageIndex = -1
    val imageSyncFrames = ticksOfNoteOnFromStart.map { midiNote ->
        imageIndex = (imageIndex + 1) % images.size
        JavaCVFrame(
            images[imageIndex],
            midiNote.durationInMidiTicks.toDouble() * timebaseInSecondPerMidiTicks
        )
    }.toMutableList()

    // Creating the black picture and saving it
    val blackEndPhoto = getBlackBufferedImage(width, height)
    val blackImageFile = File("${workDirectory.absolutePath}/lackimg.jpg")
    blackImageFile.createNewFile()
    ImageIO.write(blackEndPhoto, "jpg", blackImageFile)
    blackEndPhoto.flush()
    imageSyncFrames[imageSyncFrames.lastIndex] = JavaCVFrame(blackImageFile.absolutePath, 0.1)

    // Note: The last frame is now a black image that last for a very short time
    print(" (${(System.currentTimeMillis() - frameTimeLength) / 1000} seconds)\n")

    val encodingVideoTime = System.currentTimeMillis()
    print("Encoding the images into a video file")

    JavaCVVideoWithAudio(
        Dimensions(width, height),
        timebaseInSecondPerMidiTicks,
        formatName,
        imageSyncFrames,
        audioPath
    ).writeToFile(outputPath)

    // deleting the work directory as it is not needed anymore
    workDirectory.delete()

    print(" (${(System.currentTimeMillis() - encodingVideoTime) / 1000} seconds)\n")
    println("DONE (${(System.currentTimeMillis() - startTime) / 1000} seconds)")
}

const val arguments =
    "Arguments: <midi file path> <audio file path (put `-` if no audio)> <images folder path> <format name> <width> <height>"

private fun createFile(path: String) = File(path).createNewFile()