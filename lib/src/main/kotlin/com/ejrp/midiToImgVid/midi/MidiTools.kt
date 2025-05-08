package com.ejrp.midiToImgVid.midi

import com.ejrp.midi.StandardMidiFile
import com.ejrp.midi.data.MidiKey
import com.ejrp.midi.data.MidiVelocity
import com.ejrp.midi.data.TicksPerQuarterNoteDivision
import com.ejrp.midi.events.MidiTrackEvent
import com.ejrp.midi.events.NoteOff
import com.ejrp.midi.events.NoteOn
import com.ejrp.midi.events.SetTempo

/**
 * Get the timebase of a video in seconds per midi ticks from a midi file.
 * The function will first try to find any tempo events in the file. If there
 * are more than one tempo event, an IllegalArgumentException will be thrown.
 * If the division of this midi file is not ticks per quarter note, an IllegalArgumentException
 * will be thrown. If there is no tempo event, the tempo will be assumed to be 120 quarter notes per minutes
 * @param midiFile The file to get the timebase from
 * @return The timebase of this midi file in seconds per midi ticks
 */
fun getTimebaseFromFile(midiFile: StandardMidiFile): Double {
    // Trying to find the tempo event, if it is set
    val tempoEvent = midiFile.midiSequence.tracks
        .flatMap { it.events }
        .filterIsInstance<SetTempo>()
    require(tempoEvent.size <= 1) {
        "This program only supports a constant tempo, and there is more than one tempo event in the file"
    }
    val division = midiFile.headerChunk.midiDivision
    require(division is TicksPerQuarterNoteDivision) {
        "This program currently supports the ticks per quarter note midi division, not SMPTE division"
    }
    val ticksPerQuarterNote = division.tickPerQuarterNote // Where a quarter note is 1 beat
    return if (tempoEvent.isEmpty()) {
        val quarterNotePerMinute = 120u // This is the default tempo
        val secondsPerMinute = 60.0
        secondsPerMinute / (quarterNotePerMinute * ticksPerQuarterNote).toDouble()
    } else {
        val microSecondsPerQuarterNote = tempoEvent[0].newTempo.toDouble()
        val microSecondsPerSeconds = 1_000_000u
        microSecondsPerQuarterNote / (microSecondsPerSeconds * ticksPerQuarterNote).toDouble()
    }
}

/**
 * Represents a midi note. A midi note is a combination of a NoteOn and NoteOff midi event.
 * The two events needs to target the same key so that a midi note can be created from them.
 * The duration of the notes is the difference of the midi ticks from start of each event.
 * @property key The key (pitch) of the note
 * @property velocity The velocity of the note (how loud it is)
 * @property durationInMidiTicks The duration of the notes (in midi ticks)
 * @constructor Create a Midi note object
 */
data class MidiNote(
    val key: MidiKey,
    val velocity: MidiVelocity,
    val durationInMidiTicks: UInt
)

/**
 * Computes the total runtime of all the midi notes in seconds
 *
 * @param timebaseInSecondPerMidiTicks The timebase of video to convert from miditicks to seconds
 * @return The runtime in seconds of all the midi notes
 */
fun Array<MidiNote>.timeLength(timebaseInSecondPerMidiTicks: Double) =
    sumOf { it.durationInMidiTicks }.toDouble() * timebaseInSecondPerMidiTicks

/**
 * Finds all notes in a format 0 or format 1 midi file. A midi note is a note-on and a note-off midi event in succession
 * that both have the same key. If a note-on event is not followed by its corresponding note-off events, an
 * IllegalArgumentException will be thrown.
 *
 * IMPORTANT: If you have a format 1 file with multiple tracks containing note on and note off events you will
 * not get the expected results, because the events in multiple tracks are concatenated not interleaved
 *
 * @param midiFile The file to extract the notes ticks from
 * @return An ordered array containing the midi notes in the midi file
 */
fun getNotesTicksFromStart(midiFile: StandardMidiFile): Array<MidiNote> {
    require(midiFile.headerChunk.format == 0u.toUShort() || midiFile.headerChunk.format == 1u.toUShort()) {
        "Only format 0 or format 1 files can be inputted in this function"
    }

    val events = midiFile.midiSequence.tracks.flatMap { it.events }

    val noteEventSize = midiFile.midiSequence.tracks.flatMap { it.events }.filter { it is NoteOn || it is NoteOff }.size
    require(noteEventSize % 2 == 0) {
        "There needs to be an even amount of note events in the track\n" +
                "\tExpected: even number\n" +
                "\tActual: $noteEventSize (odd number)\n"
    }

    val midiNotes = Array(noteEventSize / 2 + 1) { MidiNote(MidiKey.CSharpMinus1, MidiVelocity.Niente, 0u) }
    var eventIndex = 0
    var noteIndex = 0
    var ticksElapsed = 0UL
    while (eventIndex < events.size) {
        val event = events[eventIndex++]

        if (event !is NoteOn) {
            ticksElapsed += event.deltaTime.quantity
            continue
        }
        val startingTickElapsed = ticksElapsed

        var noteOffEvent: MidiTrackEvent
        do {
            noteOffEvent = events[eventIndex++]
            ticksElapsed += noteOffEvent.deltaTime.quantity

        } while (noteOffEvent !is NoteOn && noteOffEvent !is NoteOff)

        if (noteOffEvent is NoteOn) {
            require(noteOffEvent.key == event.key) {
                "The next note event key does not match the previous note on event key"
            }
            require(noteOffEvent.velocity == MidiVelocity.Niente) {
                "The next event after the note on event is another note event with a velocity not equal to 0\n," +
                        "which would mean that two notes will be played at the same time, which not allowed"
            }
        }
        if (noteOffEvent is NoteOff) {
            // I have to do this else if because kotlin does not allow duck typing
            require(noteOffEvent.key == event.key) {
                "The next note event key does not match the previous note on event key"
            }
        }

        midiNotes[noteIndex++] = MidiNote(
            event.key,
            event.velocity,
            (ticksElapsed - startingTickElapsed).toUInt()
        )
    }
    midiNotes[midiNotes.lastIndex] = MidiNote(
        MidiKey.CSharpMinus1,
        MidiVelocity.Niente,
        0u
    )
    return midiNotes
}