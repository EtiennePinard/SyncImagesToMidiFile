package com.ejrp

import com.ejrp.midi.getNotesTicksFromStart
import com.ejrp.midi.getTimebaseFromFile
import com.ejrp.midi.parseMidiFile
import java.io.File
import java.io.FileInputStream

fun main(args: Array<String>) {
    require(args.size == 1) { midiCheckerArguments }

    val midiPath = args[0]
    require(File(midiPath).isValidFile())

    val midiFile = FileInputStream(midiPath).parseMidiFile()

    if (midiFile.headerChunk.format != 0.toUShort() && midiFile.headerChunk.format != 1.toUShort()) {
        println("The midi format is not format 0 or 1 so we cannot examine the midi file further")
        return
    }

    val timebaseInSecondPerMidiTicks = getTimebaseFromFile(midiFile)
    println("The time base in seconds per midi ticks is ${timebaseInSecondPerMidiTicks.double}")

    val ticksOfNoteOnFromStart = getNotesTicksFromStart(midiFile)

    println("There are ${ticksOfNoteOnFromStart.size} midi notes in this midi file")

    val minLengthMidiNote = ticksOfNoteOnFromStart.minBy { it.durationInMidiTicks }
    println("The shortest note is $minLengthMidiNote midi ticks and takes ${timebaseInSecondPerMidiTicks.double * minLengthMidiNote.durationInMidiTicks.toLong()} seconds")

    val maxLengthMidiNote = ticksOfNoteOnFromStart.maxBy { it.durationInMidiTicks }
    println("The longest note is $maxLengthMidiNote midi ticks and takes ${timebaseInSecondPerMidiTicks.double * maxLengthMidiNote.durationInMidiTicks.toLong()} seconds")
}

const val midiCheckerArguments = "Arguments: <midi file path>"