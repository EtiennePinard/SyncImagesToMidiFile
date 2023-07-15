package com.ejrp.midi

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.experimental.and

private const val MThd_MAGIC = 0x4D546864u // 'MThd'
private const val MTrk_MAGIC = 0x4D54726Bu // 'MTrk'

/**
 * If midi data is non valid
 * @constructor Creates a non valid midi data exception
 * @param cause String that tells why the midi data is invalid
 */
class NonValidMidiDataException(cause: String): Exception(cause)

/**
 * A class that represents a type 0 or type 1 midi file
 * @property headerChunk The header chunk of this midi file
 * @property midiSequence Data class that contains all the track chunks of the midi file
 * @constructor Create empty Midi file
 * @see <a href="https://www.midi.org/specifications">https://www.midi.org/specifications></a>
 */
class MidiFile(val headerChunk: HeaderChunk, val midiSequence: MidiSequence) {

    fun expandedStringRepresentation(): String {
        val builder = StringBuilder()
        builder.append("File Size: ${size()} bytes\n")
        builder.append("HeaderChunk:\n")
        builder.append("\tData: ${headerChunk.data.toHexStringList()}\n")
        builder.append("\tType: ${headerChunk.type}\n")
        builder.append("\tLength: ${headerChunk.length}\n")
        builder.append("\tFile Format: ${headerChunk.format}\n")
        builder.append("\tNumber Of Tracks: ${headerChunk.numberOfTrackChunks}\n")
        builder.append("\tDivision: ${headerChunk.division}\n")
        builder.append("Sequence:\n")
        for (i in midiSequence.tracks.indices) {
            val midiTrackEvents = midiSequence.tracks[i]
            builder.append("\tTrack #${midiSequence.tracks.indexOf(midiTrackEvents)}:\n")
            midiTrackEvents.events.forEach { midiTrackEvent ->
                builder.append("\t\t$midiTrackEvent\n")
            }
        }
        return builder.toString()
    }

    fun size(): Int {
        var result = headerChunk.data.size
        midiSequence.tracks.forEach { trackChunk -> result += trackChunk.data.size }
        return result
    }

    fun writeToFile(file: File) {
        val outputStream = file.outputStream()
        outputStream.write(headerChunk.data)
        midiSequence.tracks.forEach { trackChunk -> outputStream.write(trackChunk.data) }
        outputStream.close()
    }
}

/**
 * Parses a file into a midi file object
 * @return The midi file object parsed from the input stream
 * @throws NonValidMidiDataException If the midi data is invalid
 */
@Throws(NonValidMidiDataException::class)
fun InputStream.parseMidiFile(): MidiFile {
    // Managing the header
    val headerChunk = this.nextHeaderChunk()
    require(headerChunk.format.toUInt() == 0u || headerChunk.format.toUInt() == 1u) {
        "This parser only parses type 0 or type 1 midi files :( Apologies mate"
    }
    val size = this.available()
    val outputStream = ByteArrayOutputStream()
    this.transferTo(outputStream)
    this.close()
    val tracks = ArrayList<TrackChunk>()
    while (size > 0) {
        val newStream = ByteArrayInputStream(outputStream.toByteArray())
        for (track in tracks) newStream.skipNBytes(track.length.toLong() + 8)
        if (newStream.available() < 8) {
            if (newStream.available() == 0) {
                break
            } else {
                throw NonValidMidiDataException("There is excess bytes at the end of this midi file")
            }
        }
        tracks.add(extractTrackChunk(newStream))
    }
    return MidiFile(headerChunk, MidiSequence(tracks))
}

/**
 * A class that represents a midi chunk
 * @property data The data associated to this midi chunk
 * @constructor Creates a midi chunk with its data
 */
open class MidiChunk(val data: ByteArray) {
    val type: UInt
        get() {
            return data.subArray(0,4).getU32FromBytes()
        }
    open val length: UInt
        get() {
            return data.subArray(4,8).getU32FromBytes()
        }
    init {
        require(data.size >= 8) {
            "The minimum size for a midi chunk is 8, which represents two u32 number, which are the type and the length of the chunk."
        }
    }

    override fun toString(): String {
        return "MidiChunk(data=${data.toHexStringList()}, type=$type, length=$length)"
    }
}

class HeaderChunk(data: ByteArray): MidiChunk(data) {
    init {
        require(type == MThd_MAGIC) {
            "The first 4 bytes of the track chunk is not its type\n" +
                    "Expected: $MTrk_MAGIC\n" +
                    "Actual: \"$type\""
        }
        require(data.size == 14) {
            "The size of a header chunk in a midi file is 14\n" +
                    "Expected: 14\n" +
                    "Actual: ${data.size}"
        }
    }
    val format: UShort = data.subArray(8,10).getU16FromBytes()
    val numberOfTrackChunks: UShort = data.subArray(10, 12).getU16FromBytes()
    val division: Division
        get() {
            val division = data.subArray(12, 14)
            val bit15 = division[0]
            return if (bit15.getBit(7))
                SMPTE(division)
            else
                TicksPerQuarterNote(division)
        }

    override fun toString(): String {
        return "HeaderChunk(type=$type, length=$length, format=$format, numberOfTrackChunks=$numberOfTrackChunks, division=$division)"
    }

}

open class Division(val data: ByteArray) {
    init {
        require(data.size == 2) {
            "The size of the division data in a midi file is two bytes"
        }
    }
}
class TicksPerQuarterNote(data: ByteArray): Division(data) {
    val tickPerQuarterNote = data.getU16FromBytes()
    override fun toString(): String {
        return "TicksPerQuarterNote(tickPerQuarterNote=$tickPerQuarterNote, data=${data.toHexStringList()})"
    }
}
class SMPTE(data: ByteArray): Division(data) {
    val smpteFormat: Byte // Note: Did not test, therefore is probably wrong
        get() {
            return data[0] and 0x7F
        }
    val ticksPerFrame: Byte = data[1]

    init {
        require(
            smpteFormat.toInt() == -24 ||
                    smpteFormat.toInt() == -25 ||
                    smpteFormat.toInt() == -29 ||
                    smpteFormat.toInt() == -30
        ) {
            "The number $smpteFormat is not part of the four standard SMPTE and MIDI Time Code formats which are -24, -25, -29, -30"
        }
    }
}

data class MidiSequence(val tracks: List<TrackChunk>)
class TrackChunk(data: ByteArray, trackSize: UInt): MidiChunk(data) {
    override val length: UInt = trackSize

    init {
        require(type == MTrk_MAGIC) {
            "The first 4 bytes of the track chunk is not its type\n" +
                    "Expected: $MTrk_MAGIC\n" +
                    "Actual: \"$type\""
        }
        require(length > 0u) {
            "A track chunk needs to have at least one Midi Track event and so its length cannot be ${length}, which is 0."
        }
        require(length + 8u == data.size.toUInt()) {
            "The length of this track chunks plus 8 needs to equal the size of its data\n" +
                    "Expected: ${data.size}\n" +
                    "Actual: ${length + 8u}"
        }
    }

    val events: List<MidiTrackEvent>
        get() {
            return extractMidiEventsFromByteArray(data.subArray(8, length.toInt() + 8))
        }
}

enum class MidiEventType {
    MIDI_MESSAGE,
    SYSEX_EVENT,
    META_EVENT;
}

open class MidiTrackEvent(val tickFromStart: ULong, val data: ByteArray, val type: MidiEventType) {
    init {
        require(data.isNotEmpty()) {
            "A midi event need to have at least one status byte"
        }
    }
    val statusByte = data[0]
    val midiChannel: Byte
        get() {
            if (type == MidiEventType.SYSEX_EVENT || type == MidiEventType.META_EVENT) return -1
            return statusByte and 0xF
        }

    override fun toString(): String {
        return "MidiTrackEvent(tickFromStart=$tickFromStart, data=${data.toHexStringList()}, type=$type)"
    }
}

class NoteOnEvent(tickFromStart: ULong, status: Byte, key: Byte, velocity: Byte)
    : MidiTrackEvent(tickFromStart, byteArrayOf(status, key, velocity), MidiEventType.MIDI_MESSAGE) {
    init {
        require((status.toUByte().toInt() shr 4) == 0b1001) {
            "The status byte ${status.toHexString()} is not equal to ${(0b1001).toByte().toHexString()}"
        }
        require(!key.getBit(7)) {
            "The key $key most significant byte is not 0"
        }
        require(!velocity.getBit(7)) {
            "The velocity $velocity most significant byte is not 0"
        }
    }

    val key = data[1]
    val velocity = data[2]

    override fun toString(): String {
        return "NoteOnEvent(tickFromStart=$tickFromStart, status=${statusByte.toHexString()}, key=${data[1]}, velocity=${data[2]})"
    }
}

class NoteOffEvent(tickFromStart: ULong, status: Byte, key: Byte, velocity: Byte)
    : MidiTrackEvent(tickFromStart, byteArrayOf(status, key, velocity), MidiEventType.MIDI_MESSAGE) {
    init {
        require((status.toUByte().toInt() shr 4) == 0b1000) {
            "The status byte ${status.toHexString()}"
        }
        require(!key.getBit(7)) {
            "The key $key most significant byte is not 0"
        }
        require(!velocity.getBit(7)) {
            "The velocity $velocity most significant byte is not 0"
        }
    }

    val key = data[1]
    val velocity = data[2]

    override fun toString(): String {
        return "NoteOffEvent(tickFromStart=$tickFromStart, status=${statusByte.toHexString()}, key=${data[1]}, velocity=${data[2]})"
    }
}

private fun InputStream.nextHeaderChunk() = HeaderChunk(this.readNBytes(14))

/**
 * Extract the next track chunk from an input stream.
 * It will consume the input stream and so if you
 * want to keep the original input stream you need to
 * clone it before putting it into this method
 * @return A track chunk from this input stream
 * @throws IllegalArgumentException If the stream is less than 8
 */
@Throws(IllegalArgumentException::class)
// @Contract(mutates = "param1")
private fun extractTrackChunk(stream: InputStream): TrackChunk {
    if (stream.available() < 8) throw IllegalArgumentException("The stream is less than 8, the minimum length of a track chunk")
    val skippedBytes = stream.readNBytes(8)
    val trackSize = skippedBytes.subArray(4,8).getU32FromBytes().toInt()
    val outputStream = ByteArrayOutputStream()
    stream.transferTo(outputStream)
    val endOfTrackLength = getLengthOfTrack(ByteArrayInputStream(outputStream.toByteArray()))
    return if (trackSize == endOfTrackLength) // The midi file is a good file, both numbers match
        TrackChunk(
            skippedBytes.plus(ByteArrayInputStream(outputStream.toByteArray()).readNBytes(trackSize)),
            trackSize.toUInt()
        )
    else if (endOfTrackLength != -1) // The midi file is not a good file, since the track size is lying
        TrackChunk(
            skippedBytes.plus(ByteArrayInputStream(outputStream.toByteArray()).readNBytes(endOfTrackLength)),
            endOfTrackLength.toUInt()
        )
    else // The midi file does not have an end of track event
        TrackChunk(
            skippedBytes.plus(ByteArrayInputStream(outputStream.toByteArray()).readNBytes(trackSize)),
            trackSize.toUInt()
        )
}

/**
 * Tries to get the length of a midi track chunk based
 * on the end of track event. This function will consume
 * the input stream and so if you want to keep the original
 * input stream you need to clone it before putting it into this method
 * @return The length of the track or -1 if the end of the file was reached
 */
@Throws(NonValidMidiDataException::class)
// @Contract(mutates = "param1")
private fun getLengthOfTrack(stream: InputStream): Int {
    var length = 1 // Since we already read a byte with the previous variable
    var reachedEnOfFile = false
    var reachedEndOfTrackEvent = false
    var previous = stream.read()
    while (!reachedEnOfFile && !reachedEndOfTrackEvent) {
        val currentByte = stream.read()
        // println("Previous Byte: ${previous.toByte().toHex()}, Current Byte: ${currentByte.toByte().toHex()}")

        if (currentByte == -1) {
            reachedEnOfFile = true
            continue
        }
        if (previous == 0xFF && currentByte == 0x2F) {
            if (stream.read() == 0x00) {
                length += 2
                reachedEndOfTrackEvent = true
                continue
            } else {
                throw NonValidMidiDataException("The end of track event does not have a length of 0")
            }
        }
        previous = currentByte
        length++
    }
    return if (reachedEndOfTrackEvent) length else -1
}

/**
 * Extracts midi events from a byte array
 * @param trackData The byte array to extract the events from
 * @return The list of midi events extracted from the byte array
 */
@Throws(NonValidMidiDataException::class)
private fun extractMidiEventsFromByteArray(trackData: ByteArray): List<MidiTrackEvent> {
    val events = ArrayList<MidiTrackEvent>()
    var i = 0
    var tickFromStartOfTrack: ULong = 0u
    var lastStatus = -1
    while (i < trackData.size) {
        // Could not always have to split the array by the total size, just how big a
        // variable length quantity can be
        val deltaTime = trackData.subArray(i, trackData.size).nextVariableLengthQuantity()
        i += deltaTime.length
        tickFromStartOfTrack += deltaTime.quantity
        // Trash logic, but I want my `val` and not `var`!!!!
        val currentStatus = if (!trackData[i].getBit(7)) {
            if (lastStatus == -1) {
                throw NonValidMidiDataException("Unknown status byte ${trackData[i].toHexString()}")
            } else {
                i--
                lastStatus
            }
        } else {
            trackData[i].toUByte().toInt()
        }

        // Handling the midi event
        when (currentStatus shr 4) {
            0b1000 -> { // Note Off
                val key = trackData[i + 1]
                val velocity = trackData[i + 2]
                events.add(
                    NoteOffEvent(
                        tickFromStartOfTrack,
                        currentStatus.toByte(),
                        key,
                        velocity
                    )
                )
                i += 3
            }
            0b1001 -> { // Note On
                val key = trackData[i + 1]
                val velocity = trackData[i + 2]
                events.add(
                    NoteOnEvent(
                        tickFromStartOfTrack,
                        currentStatus.toByte(),
                        key,
                        velocity
                    )
                )
                i += 3
            }
            0b1010, // Polyphonic Key Pressure (Aftertouch)
            0b1110, // Pitch Wheel Change
            0b1011 -> { // Control Change or Channel Mode Messages
                events.add(
                    MidiTrackEvent(
                        tickFromStartOfTrack,
                        byteArrayOf(currentStatus.toByte()).plus(trackData.subArray(i + 1, i + 3)),
                        MidiEventType.MIDI_MESSAGE
                    )
                )
                i += 3
            }
            0b1100, // Program Change
            0b1101 -> { // Channel Pressure (After-touch)
                events.add(
                    MidiTrackEvent(
                        tickFromStartOfTrack,
                        byteArrayOf(currentStatus.toByte()).plus(trackData.subArray(i + 1, i + 2)),
                        MidiEventType.MIDI_MESSAGE
                    )
                )
                i += 2
            }
            0b1111 -> {
                // Either a sysex message or a Meta-Event
                when (currentStatus) {
                    0xFF -> { // Meta Message
                        val length = trackData[i + 2].toUByte()
                        events.add(
                            MidiTrackEvent(
                                tickFromStartOfTrack,
                                byteArrayOf(currentStatus.toByte(), trackData[i + 1], trackData[i + 2])
                                    .plus(trackData.subArray(i + 3, i + length.toInt() + 3)),
                                MidiEventType.META_EVENT
                            )
                        )
                        i += length.toInt() + 3
                        if (events.last().data[1].toInt() == 0x2F) {
                            // We found an end of track event
                            break
                        }
                    }
                    0xF0, 0xF7 -> { // System exclusive message
                        val length = trackData[i + 1]
                        i++
                        events.add(
                            MidiTrackEvent(
                                tickFromStartOfTrack,
                                byteArrayOf(currentStatus.toByte(), length)
                                    .plus(trackData.subArray(i + 2, i + 2 + length.toInt())),
                                MidiEventType.SYSEX_EVENT
                            )
                        )
                        i += 2 + length.toInt()
                    }
                    else -> {
                        throw NonValidMidiDataException(
                            "The byte of this midi, ${
                                currentStatus.toByte().toHexString()
                            }, event which starts with 0b1111, does not match any Sysex or Meta Midi Message!"
                        )
                    }
                }
            }
            else -> {
                throw NonValidMidiDataException(
                    "The status byte of this midi event, which is ${
                        currentStatus.toByte().toHexString()
                    } is not a valid!"
                )
            }
        }
        lastStatus = currentStatus
    }
    return events
}

/**
 * Returns a new ByteArray with the elements
 * starting from the start index (inclusive)
 * to the end index (exclusive)
 * @param start The index of the element of this array which will be the first one of the sub array
 * @param end The index of the element of this array which will be the last one of the sub array
 */
fun ByteArray.subArray(start: Int, end: Int): ByteArray {
    if (end<= start) {
        return ByteArray(0)
    }
    // No need for checks since there are already done in the native arraycopy method
    val result = ByteArray(end - start)
    System.arraycopy(this, start, result, 0, result.size)
    return result
}

/**
 * Gets the bit at the index, where 7 is the most significant bit (the first bit,
 * in other words the bit that is the most left) and 0 is the least significant bit,
 * or the bit which is the furthest right
 * @param bitIndex The index of the bit
 * @return A boolean which represents a bit
 */
private fun Number.getBit(bitIndex: Int) = this.toInt() and (1 shl bitIndex) != 0

/**
 * A data class which represent a variable length quantity of no more than 4 bytes.
 * @property quantity A positive u32 number no more than 0x0FFFFFFF
 * @property length The number of bytes in the quantity, which is from 1 to 4
 * @constructor Creates a Variable length quantity with a quantity and a length
 * @see  <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">https://en.wikipedia.org/wiki/Variable-length_quantity</a>
 */
private data class VariableLengthQuantity(val quantity: UInt, val length: Int) {
    init {
        require(quantity <= 0x0FFFFFFFu) {
            "The quantity of a variable length quantity is from 0 to 0x0FFFFFFF.\n" +
                    "Expected: quantity <= 0x0FFFFFFF\n" +
                    "Actual: $quantity"
        }
        require(length in 1..4) {
            "The number of bytes in a variable length quantity is from 1 to 4.\n" +
                    "Expected: 0 - 4\n" +
                    "Actual: $length"
        }
    }
}

/**
 * Gets the next variable length quantity from the beginning of the array
 * @return The next variable length quantity from the beginning of the array
 */
private fun ByteArray.nextVariableLengthQuantity(): VariableLengthQuantity {
    require(this.isNotEmpty()) {
        "The array that you want to get the variable length quantity is empty"
    }
    var result = 0u
    // First seven bytes is significant
    // For the last byte, bit seven will be clear
    var length = 0
    for (i in 0..3) { // Since you cannot have more than 4 bytes in a variable length quantity
        val nextByte = this[i]
        result = result shl 7 or (nextByte.toInt() and 0x7F).toUInt()
        if (!nextByte.getBit(7)) { // If bit 7 is not set
            length = i + 1
            break
        }
    }
    require(length > 0) {
        "The variable length quantity of this array is bigger than 4 bytes!"
    }
    return VariableLengthQuantity(result, length)
}

/**
 * Transforms a byte array of size 2 to an unsigned 16 bit number
 * @return An unsigned 16 bit number from a byte array of size 2
 */
private fun ByteArray.getU16FromBytes(): UShort {
    require(this.size == 2) { "The byte array provided in the getU32FromBytes function has a size $size which is not equal to 2" }
    var result: UInt = this[0].toUInt() shl 8
    result = result or this[1].toUByte().toUInt()
    return result.toUShort()
}

/**
 * Transforms a byte array of size 4 to an unsigned 32 bit number
 * @return An unsigned 32 bit number from a byte array of size 4
 */
fun ByteArray.getU32FromBytes(): UInt {
    require(this.size == 4) { "The byte array provided in the getU32FromBytes function has a size $size which is not equal to 4" }
    var result: UInt = 0u
    for (i in 0 until 4)
        result += this[i].toUByte().toUInt() shl ((3 - i) * 8) // 8 = 0b1000 so a 4 byte shift if 3 - i == 1
    return result
}

/**
 * Convert a byte to a hex string with format 0x(first four bytes)(Last four bytes)
 * @return The hex representation of this byte as a string
 */
private fun Byte.toHexString(): String {
    val bytesToInt = this.toUByte().toInt()
    return "0x" + Integer.toHexString(bytesToInt shr 4) + Integer.toHexString(bytesToInt and 0xF)
}

/**
 * Converts a byte array to a hex string list for easy logging
 * @return A list of strings which represent the bytes of the original array
 */
private fun ByteArray.toHexStringList() = this.map { it.toHexString() }
