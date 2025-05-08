package com.ejrp.midi

import com.ejrp.midi.data.*
import com.ejrp.midi.events.EndOfTrack
import com.ejrp.midi.events.NoteOff
import com.ejrp.midi.events.NoteOn
import com.ejrp.midi.events.SetTempo
import com.ejrp.midi.events.SystemExclusiveEvent
import com.ejrp.midi.events.SystemExclusiveFormat
import com.ejrp.midi.events.TextEvent
import com.ejrp.midi.utils.VariableLengthQuantity
import com.ejrp.midiToImgVid.midi.MidiNote
import com.ejrp.midiToImgVid.midi.getNotesTicksFromStart
import com.ejrp.midiToImgVid.midi.getTimebaseFromFile
import com.ejrp.midiToImgVid.midi.timeLength
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MidiToolsTest {
    val C_Major_Scale = StandardMidiFile(
        HeaderChunk(0u, 1u, TicksPerQuarterNoteDivision(100u)),
        MidiSequence(
            listOf(
                TrackChunk(
                    listOf(
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.C4, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.C4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.D4, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.D4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.E4, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.E4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.F4, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.F4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.G4, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.G4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.A4, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.A4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.B4, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.B4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.C5, MidiVelocity.Piano),
                        NoteOff(VariableLengthQuantity(100u), 0, MidiKey.C5, MidiVelocity.Piano),
                        EndOfTrack(VariableLengthQuantity(0u))
                    )
                )
            )
        )
    )

    val complicatedCMajor = StandardMidiFile(
        HeaderChunk(0u, 1u, TicksPerQuarterNoteDivision(100u)),
        MidiSequence(
            listOf(
                TrackChunk(
                    listOf(
                        SetTempo(VariableLengthQuantity(0u), 428571u), // 140 bpm
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.C4, MidiVelocity.Piano),
                        TextEvent(VariableLengthQuantity(50u), ""),
                        NoteOn(VariableLengthQuantity(50u), 0, MidiKey.C4, MidiVelocity.Niente),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.D4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(100u), 0, MidiKey.D4, MidiVelocity.Niente),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.E4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(100u), 0, MidiKey.E4, MidiVelocity.Niente),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.F4, MidiVelocity.Piano),
                        SystemExclusiveEvent(
                            VariableLengthQuantity(50u),
                            SystemExclusiveFormat.F7_FORMAT,
                            ByteArray(0)
                        ),
                        NoteOn(VariableLengthQuantity(50u), 0, MidiKey.F4, MidiVelocity.Niente),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.G4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(100u), 0, MidiKey.G4, MidiVelocity.Niente),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.A4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(100u), 0, MidiKey.A4, MidiVelocity.Niente),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.B4, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(100u), 0, MidiKey.B4, MidiVelocity.Niente),
                        NoteOn(VariableLengthQuantity(0u), 0, MidiKey.C5, MidiVelocity.Piano),
                        NoteOn(VariableLengthQuantity(100u), 0, MidiKey.C5, MidiVelocity.Niente),
                        EndOfTrack(VariableLengthQuantity(0u))
                    )
                )
            )
        )
    )

    @Test
    fun test_getTimebaseFromFile() {
        val expected1 = 0.005
        val actual1 = getTimebaseFromFile(C_Major_Scale)
        assertEquals(
            expected1,
            actual1
        )
        val expected2 = 0.00428571
        val actual2 = getTimebaseFromFile(complicatedCMajor)
        assertEquals(
            expected2,
            actual2
        )
    }

    @Test
    fun test_getNotesTicksFromStart() {
        val expected = arrayOf(
            MidiNote(MidiKey.C4, MidiVelocity.Piano, 100u, 0u),
            MidiNote(MidiKey.D4, MidiVelocity.Piano, 100u, 100u),
            MidiNote(MidiKey.E4, MidiVelocity.Piano, 100u, 200u),
            MidiNote(MidiKey.F4, MidiVelocity.Piano, 100u, 300u),
            MidiNote(MidiKey.G4, MidiVelocity.Piano, 100u, 400u),
            MidiNote(MidiKey.A4, MidiVelocity.Piano, 100u, 500u),
            MidiNote(MidiKey.B4, MidiVelocity.Piano, 100u, 600u),
            MidiNote(MidiKey.C5, MidiVelocity.Piano, 100u, 700u),
            MidiNote(MidiKey.CSharpMinus1, MidiVelocity.Niente, 0u, 800u)
        )
        val actual1 = getNotesTicksFromStart(C_Major_Scale)
        assertArrayEquals(expected, actual1)

        val actual2 = getNotesTicksFromStart(complicatedCMajor)
        assertArrayEquals(expected, actual2)
    }

    @Test
    fun test_timeLength() {
        val expected1 = 4.0
        val actual1 = getNotesTicksFromStart(C_Major_Scale).timeLength(getTimebaseFromFile(C_Major_Scale))
        assertEquals(
            expected1,
            actual1
        )

        val expected2 = 3.428568
        val actual2 = getNotesTicksFromStart(complicatedCMajor).timeLength(getTimebaseFromFile(complicatedCMajor))
        assertEquals(
            expected2,
            actual2
        )
    }

}