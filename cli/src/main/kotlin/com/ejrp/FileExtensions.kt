package com.ejrp

import java.io.File

/**
 * Is the file path provided valid
 * @return True if the file exists, is a file, and it can be read, else false
 */
fun File.isValidFile(): Boolean {
    return when {
        !exists() -> {
            println("The file at the path $path does not exist")
            false
        }

        !isFile -> {
            println("The file at the $path is not a file")
            false
        }

        !canRead() -> {
            println("This program does not have read permission for the file at the path $path")
            false
        }

        else -> true
    }
}

/**
 * Is the folder path provided valid
 * @return True if the folder exists, is a directory and its contents can be read, else false
 */
fun File.isValidFolder(): Boolean {
    return when {
        !exists() -> {
            println("The folder at the path $path does not exist")
            false
        }

        !isDirectory -> {
            println("The folder at the $path is not a folder")
            false
        }

        !canRead() -> {
            println("This program does not have read permission for the folder at the path $path")
            false
        }

        else -> true
    }
}