package com.ejrp

import java.io.File

/**
 * Is the file path provided valid
 *
 * @param debug Should the function output the reason why the file is invalid. Default: true
 * @return True if the file exists, is a file, and it can be read, else false
 */
fun File.isValidFile(debug: Boolean = true): Boolean {
    return when {
        !exists() -> {
            if (debug)
                println("The file at the path $path does not exist")
            false
        }

        !isFile -> {
            if (debug)
                println("The file at the $path is not a file")
            false
        }

        !canRead() -> {
            if (debug)
                println("This program does not have read permission for the file at the path $path")
            false
        }

        else -> true
    }
}

/**
 * Is the folder path provided valid
 * @param debug Should the function output the reason why the folder is invalid. Default: true
 * @return True if the folder exists, is a directory and its contents can be read, else false
 */
fun File.isValidFolder(debug: Boolean = true): Boolean {
    return when {
        !exists() -> {
            if (debug)
                println("The folder at the path $path does not exist")
            false
        }

        !isDirectory -> {
            if (debug)
                println("The folder at the $path is not a folder")
            false
        }

        !canRead() -> {
            if (debug)
                println("This program does not have read permission for the folder at the path $path")
            false
        }

        else -> true
    }
}