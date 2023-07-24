package com.ejrp.video

import io.humble.video.*
import io.humble.video.awt.MediaPictureConverterFactory
import java.awt.image.BufferedImage

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
 * Represents a video made with the Humble library
 * @property dimensions The width and the height of the video
 * @property timebase The timebase of the video. It is a value that will be
 * multiplied by the timestamp of each frame to determine the frames length in seconds
 * @property formatName The name of the video file format of this video. Ex: "mp4"
 * @property frames A list of frames that contain the image of a frame and its timestamp
 * @constructor Creates a Video object
 */
class HumbleVideo(
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