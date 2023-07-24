package com.ejrp.video

import io.humble.video.*
import io.humble.video.awt.MediaPictureConverterFactory
import java.awt.image.BufferedImage

/**
 * A data class to hold objects that are important to audio processing in humble
 * @property demuxer The demuxer to demuxe the audio file
 * @property audioDecoder The decoder to decode the audio file
 * @property audioStreamId The stream id of the audio stream in the demuxer
 * @constructor Create a new AudioObjects object
 */
private data class AudioObjects(val demuxer: Demuxer, val audioDecoder: Decoder, val audioStreamId: Int)

/**
 * Represents a video with audio made with the Humble library
 * @property dimensions The width and the height of the video
 * @property timebase The timebase of the video. It is a value that will be
 * multiplied by the timestamp of each frame to determine the frames length in seconds
 * @property formatName The name of the video file format of this video. Ex: "mp4"
 * @property frames A list of frames that contain the image of a frame and its timestamp
 * @property audioFilePath The path of the audio file that will be added to this video
 * @constructor Creates a Video object
 */
class HumbleVideoWithAudio(
    private val dimensions: Dimensions,
    private val timebase: Rational,
    private val formatName: String,
    private val frames: List<Frame>,
    private val audioFilePath: String
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
        // This function is a derivation of these examples:
        // https://github.com/artclarke/humble-video/blob/master/humble-video-demos/src/main/java/io/humble/video/demos/RecordAndEncodeVideo.java
        // https://github.com/artclarke/humble-video/blob/master/humble-video-demos/src/main/java/io/humble/video/demos/DecodeAndPlayAudio.java
        val muxer = Muxer.make(outputPath, null, formatName)
        // Setting both encoders
        val videoEncoder = Encoder.make(Codec.findEncodingCodec(muxer.format.defaultVideoCodecId))
        setVideoEncoderOptions(videoEncoder, muxer)

        val picture = MediaPicture.make(
            videoEncoder.width,
            videoEncoder.height,
            videoEncoder.pixelFormat
        )
        picture.timeBase = videoEncoder.timeBase

        val audioObject = audioObjectFromFile()
        val samples = MediaAudio.make(
            audioObject.audioDecoder.frameSize,
            audioObject.audioDecoder.sampleRate,
            audioObject.audioDecoder.channels,
            AudioChannel.Layout.CH_LAYOUT_STEREO, // Hardcoding the channel layout, else the channel layout would be unknown
            audioObject.audioDecoder.sampleFormat
        )

        val audioEncoder = Encoder.make(Codec.findEncodingCodec(muxer.format.defaultAudioCodecId))
        setAudioEncoderOptions(audioEncoder, samples, muxer)

        // Opening the encoders and muxer
        videoEncoder.open(null, null)
        muxer.addNewStream(videoEncoder)

        audioEncoder.open(null, null)
        muxer.addNewStream(audioEncoder)

        muxer.open(null, null)

        val packet = MediaPacket.make()
        encodeFramesToMuxer(picture, videoEncoder, packet, muxer)
        encodeAudioFileToMuxer(audioObject, packet, samples, audioEncoder, muxer)

        // Cleaning up after ourselves
        audioObject.demuxer.close()
        muxer.close()
    }

    private fun encodeAudioFileToMuxer(
        audioObject: AudioObjects,
        packet: MediaPacket,
        samples: MediaAudio,
        audioEncoder: Encoder,
        muxer: Muxer
    ) {
        while (audioObject.demuxer.read(packet) >= 0) {
            if (packet.streamIndex == audioObject.audioStreamId) {
                var offset = 0
                var bytesRead = 0
                do {
                    // We decode the MediaPacket into the MediaAudio to get a complete data
                    bytesRead += audioObject.audioDecoder.decode(samples, packet, offset)
                    if (samples.isComplete) {
                        // The sample has complete data, we can now encode the audio
                        do {
                            // We encode the audio from MediaAudio to MediaPacket
                            audioEncoder.encode(packet, samples)
                            if (packet.isComplete)
                                muxer.write(packet, false)
                        } while (packet.isComplete)
                    }
                    offset += bytesRead
                } while (offset < packet.size)
            }
        }

        // Flushing any cache in the encoders
        do {
            audioObject.audioDecoder.decode(samples, null, 0)
            if (samples.isComplete) {
                do {
                    audioEncoder.encode(packet, null)
                    if (packet.isComplete)
                        muxer.write(packet, false)
                } while (packet.isComplete)
            }
        } while (samples.isComplete)
    }

    private fun encodeFramesToMuxer(
        picture: MediaPicture,
        videoEncoder: Encoder,
        packet: MediaPacket,
        muxer: Muxer
    ) {
        val converter = MediaPictureConverterFactory.createConverter(frames[0].image, picture)
        frames.forEach { frame ->
            // We need to convert a rgb color space to a Y'CbCr color space
            converter.toPicture(picture, frame.image, frame.timestamp)
            do {
                videoEncoder.encode(packet, picture)
                if (packet.isComplete) {
                    muxer.write(packet, false)
                }
            } while (packet.isComplete)
        }

        // Flushing any cache in the encoder
        do {
            videoEncoder.encode(packet, null)
            if (packet.isComplete)
                muxer.write(packet, false)
        } while (packet.isComplete)
    }

    private fun setVideoEncoderOptions(
        videoEncoder: Encoder,
        muxer: Muxer
    ) {
        videoEncoder.width = dimensions.width
        videoEncoder.height = dimensions.height
        // Most video format uses yuv420p pixel format
        videoEncoder.pixelFormat = PixelFormat.Type.PIX_FMT_YUV420P
        videoEncoder.timeBase = timebase
        // Does the format need a global header?
        if (muxer.format.getFlag(ContainerFormat.Flag.GLOBAL_HEADER)) {
            videoEncoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true)
        }
    }

    private fun audioObjectFromFile(): AudioObjects {
        val demuxer = Demuxer.make()
        demuxer.open(
            audioFilePath,
            null,
            false,
            true,
            null,
            null
        )
        var audioDecoder: Decoder? = null
        var audioStreamId = -1
        for (streamId in 0 until demuxer.numStreams) {
            val stream = demuxer.getStream(streamId)
            val decoder = stream.decoder
            if (decoder != null && decoder.codecType === MediaDescriptor.Type.MEDIA_AUDIO) {
                audioDecoder = decoder
                audioStreamId = streamId
                break
            }
        }
        check(audioDecoder != null && audioStreamId != -1) { "could not find audio stream in container $audioFilePath" }
        audioDecoder.open(null, null)

        return AudioObjects(demuxer, audioDecoder, audioStreamId)
    }

    private fun setAudioEncoderOptions(
        audioEncoder: Encoder,
        samples: MediaAudio,
        muxer: Muxer
    ) {
        audioEncoder.channels = samples.channels
        audioEncoder.channelLayout = samples.channelLayout
        audioEncoder.sampleFormat = samples.format
        audioEncoder.sampleRate = samples.sampleRate
        if (muxer.format.getFlag(ContainerFormat.Flag.GLOBAL_HEADER)) {
            audioEncoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true)
        }
    }
}

