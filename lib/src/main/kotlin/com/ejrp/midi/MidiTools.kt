package com.ejrp.midi

import io.humble.video.Rational

/**
 * Get the timebase of a video in seconds per midi ticks from a format 0 or format 1 midi file.
 * The function will first try to find any tempo events in the file. If there
 * are more than one tempo event, an IllegalArgumentException will be thrown.
 * If the division of this midi file is not ticks per quarter note, an IllegalArgumentException
 * will be thrown. If there is no tempo event, the tempo will be assumed to be 120 quarter notes per minutes
 * @param midiFile The file to get the timebase from
 * @return The timebase of this midi file in seconds per midi ticks
 */
fun getTimebaseFromFile(midiFile: MidiFile): Rational {
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
        val quarterNotePerMinute = 120 // This is the default tempo
        val secondsPerMinute = 60
        Rational.make(
            secondsPerMinute,
            quarterNotePerMinute * ticksPerQuarterNote
        )
    } else {
        val microSecondsPerQuarterNote = byteArrayOf(0).plus(
            tempoEvent[0].data.subArray(3, tempoEvent[0].data.size)
        ).getU32FromBytes().toInt()
        val microSecondsPerSeconds = 1_000_000
        Rational.make(
            microSecondsPerQuarterNote,
            microSecondsPerSeconds * ticksPerQuarterNote
        )
    }
}

/**
 * Represents a midi note. A midi note is a combination of a NoteOn and NoteOff midi event.
 * The two events needs to target the same key so that a midi note can be created from them.
 * The duration of the notes is the difference of the midi ticks from start of each event.
 * @property key The key (pitch) of the note
 * @property velocity The velocity of the note (how loud it is)
 * @property durationInMidiTicks The duration of the notes (in midi ticks)
 * @property ticksFromStart The amount of midi ticks from the start of the file
 * @constructor Create a Midi note object
 */
data class MidiNote(val key: Byte, val velocity: Byte, val durationInMidiTicks: ULong, val ticksFromStart: ULong) {
    init {
        require(key >= 0) {
            "The key $key is smaller than 0"
        }
        require(velocity >= 0) {
            "The velocity $velocity is smaller than 0"
        }
    }
}

/**
 * Finds all notes in a format 0 or format 1 midi file. A midi note is a note-on and a note-off midi event in succession
 * that both have the same key. If a note-on event is not followed by its corresponding note-off events, an
 * IllegalArgumentException will be thrown.
 * @param midiFile The file to extract the notes ticks from
 * @return An ordered array containing the midi notes in the midi file
 */
fun getNotesTicksFromStart(midiFile: MidiFile): Array<MidiNote> {
    require(midiFile.headerChunk.format < 2u) {
        "Only format 0 or format 1 files can be inputted in this function"
    }
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
    val midiNotes = Array(noteEvents.size / 2 + 1) { MidiNote(0, 0, 0uL, 0uL) }
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

                midiNotes[i / 2] = MidiNote(
                    noteOnEvent.key,
                    noteOffEvent.velocity,
                    noteOffEvent.tickFromStart - noteOnEvent.tickFromStart,
                    noteOnEvent.tickFromStart
                )
            }

            is NoteOffEvent -> {
                require(noteOffEvent.key == noteOnEvent.key) {
                    "The next note event key does not match the previous note on event key"
                }
                midiNotes[i / 2] = MidiNote(
                    noteOnEvent.key,
                    noteOffEvent.velocity,
                    noteOffEvent.tickFromStart - noteOnEvent.tickFromStart,
                    noteOnEvent.tickFromStart
                )
            }
        }
        i += 2
    }
    midiNotes[midiNotes.lastIndex] = MidiNote(
        0,
        0,
        0uL,
        midiNotes[midiNotes.lastIndex - 1].ticksFromStart + midiNotes[midiNotes.lastIndex - 1].durationInMidiTicks
    )
    return midiNotes
}