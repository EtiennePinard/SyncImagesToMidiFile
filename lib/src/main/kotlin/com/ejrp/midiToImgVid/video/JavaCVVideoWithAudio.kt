package com.ejrp.midiToImgVid.video

import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Represents a frame of video. Make sure that every image of every frame have the same dimensions
 * @property imagePath The path of the image that will be displayed for that frame
 * @property durationInSeconds The duration of this frame in seconds
 * @constructor Creates a Frame object
 */
data class JavaCVFrame(val imagePath: String, val durationInSeconds: Double)

/**
 * Represents the dimension of a video
 * @property width The width of a video
 * @property height The height of a video
 * @constructor Create a Dimensions object
 */
data class Dimensions(val width: Int, val height: Int)

/**
 * Represents a video with audio made with the JavaCV library
 * @property dimensions The width and the height of the video
 * @property timebase The timebase of the video. It is a value that will be
 * multiplied by the timestamp of each frame to determine the frames length in seconds
 * @property formatName The name of the video file format of this video. Ex: "mp4"
 * @property imageSyncFrames A list of frames that contain the image of a frame and its timestamp
 * @property audioFilePath The path of the audio file that will be added to this video
 * @constructor Creates a Video object
 */
class JavaCVVideoWithAudio(
    private val dimensions: Dimensions,
    private val timebase: Double,
    private val formatName: String,
    private val imageSyncFrames: List<JavaCVFrame>,
    private val audioFilePath: String
) {

    init {
        imageSyncFrames.forEach { frame ->
            val image = try {
                ImageIO.read(File(frame.imagePath))
            } catch (e: Exception) {
                println("\nERROR reading file name ${frame.imagePath}")
                e.printStackTrace()
                exitProcess(1)
            }
            require(image.width == dimensions.width) {
                "The width ${image.width} of the image does not equal the width of the video ${dimensions.width} of picture ${frame.imagePath}"
            }
            require(image.height == dimensions.height) {
                "The height ${image.height} of the image does not equal the height of the video ${dimensions.height} of picture ${frame.imagePath}"
            }
            require(image.type == BufferedImage.TYPE_3BYTE_BGR) {
                "The type ${image.type} of this image is not TYPE_3BYTE_BGR (${BufferedImage.TYPE_3BYTE_BGR}) of picture ${frame.imagePath}"
            }
            image.flush()
        }
    }

    fun writeToFile(outputPath: String) {
        val converter = Java2DFrameConverter()
        val recorder = FFmpegFrameRecorder(outputPath, dimensions.width, dimensions.height, 2)
        recorder.format = formatName
        recorder.videoCodec = AV_CODEC_ID_H264
        recorder.videoBitrate = 12 * 1024 * 1024 // 12_000 Kbps
        recorder.audioCodec = AV_CODEC_ID_AAC
        recorder.frameRate = 1 / timebase // 1 / timebase is also called the timescale
        recorder.audioBitrate = 192000
        recorder.sampleRate = 44100

        val audioGrabber = FFmpegFrameGrabber(audioFilePath)
        audioGrabber.start()
        recorder.audioChannels = audioGrabber.audioChannels
        recorder.sampleRate = audioGrabber.sampleRate

        recorder.start()

        // Write image frames
        for (imageSyncFrame in imageSyncFrames) {
            val image = ImageIO.read(File(imageSyncFrame.imagePath))
            val frame = converter.convert(image)
            val framesNeeded = (imageSyncFrame.durationInSeconds / timebase).toInt()
            repeat(framesNeeded) {
                recorder.record(frame)
            }

            image.flush()
        }

        // Flush any remaining video frames in the encoder
        try {
            recorder.record(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Write audio
        var audioFrame: Frame?
        while (audioGrabber.grab().also { audioFrame = it } != null) {
            if (audioFrame!!.samples != null) {
                recorder.record(audioFrame)
            }
        }

        // Flush any remaining audio in the encoder
        try {
            recorder.record(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        recorder.stop()
        audioGrabber.stop()
    }
}
