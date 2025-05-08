package com.ejrp

import com.ejrp.midi.StandardMidiFile
import com.ejrp.midiToImgVid.midi.getNotesTicksFromStart
import com.ejrp.midiToImgVid.midi.getTimebaseFromFile
import com.ejrp.midiToImgVid.midi.timeLength
import java.io.File
import java.io.FileInputStream
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { midiCheckerArguments }

    val midiPath = args[0]
    require(File(midiPath).isValidFile())

    val midiFile = StandardMidiFile.fromInputStream(FileInputStream(midiPath))

    if (midiFile.headerChunk.format != 0u.toUShort() && midiFile.headerChunk.format != 1u.toUShort()) {
        println("The midi format is not format 0 or format 1 so we cannot examine the midi file further")
        return
    }

    val timebaseInSecondPerMidiTicks = getTimebaseFromFile(midiFile)
    println("The time base in seconds per midi ticks is $timebaseInSecondPerMidiTicks")

    val ticksOfNoteOnFromStart = getNotesTicksFromStart(midiFile)

    println("There are ${ticksOfNoteOnFromStart.size} midi notes in this midi file")
    println("\tNote: The last note is just a zero length note to make the second to last note last for the its required duration.")
    println("\t      Therefore the amount of notes that you can use to sync images is actually ${ticksOfNoteOnFromStart.size - 1}")

    val minLengthMidiNote = ticksOfNoteOnFromStart.minBy { it.durationInMidiTicks }
    println("The shortest note is $minLengthMidiNote midi ticks and takes ${timebaseInSecondPerMidiTicks * minLengthMidiNote.durationInMidiTicks.toLong()} seconds")

    val maxLengthMidiNote = ticksOfNoteOnFromStart.maxBy { it.durationInMidiTicks }
    println("The longest note is $maxLengthMidiNote midi ticks and takes ${timebaseInSecondPerMidiTicks * maxLengthMidiNote.durationInMidiTicks.toLong()} seconds")

    val runtime = ticksOfNoteOnFromStart.timeLength(timebaseInSecondPerMidiTicks)
    println("The total runtime of this midi file is ${runtime.seconds}")

    if (args.size >= 2 && args[1] == "-debug") println(midiFile.expandedStringRepresentation())
    else println("Enable the debug flag to see more information")
}

const val midiCheckerArguments = "Arguments: <midi file path> <debug flag>\n" +
        "\tEnable the debug flag with -debug to print the contents of the midi file\n" +
        "\tIf you are running this program with the gradle runMidiVerifier task use the -PrunArgs=\"<arguments>\" " +
        "option in the command to pass command line arguments to this program"