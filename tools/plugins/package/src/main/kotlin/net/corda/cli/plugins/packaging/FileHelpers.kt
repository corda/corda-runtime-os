package net.corda.cli.plugins.packaging

import java.nio.file.Files
import java.nio.file.Path

internal object FileHelpers {
    /**
     * Check file exists and returns a Path object pointing to the file, throws error if file does not exist
     */
    fun requireFileExists(fileName: String): Path {
        val path = Path.of(fileName)
        require(Files.isReadable(path)) { "\"$fileName\" does not exist or is not readable" }
        return path
    }

    /**
     * Check that file does not exist and returns a Path object pointing to the filename, throws error if file exists
     */
    fun requireFileDoesNotExist(fileName: String): Path {
        val path = Path.of(fileName)
        require(Files.notExists(path)) { "\"$fileName\" already exists" }
        return path
    }
}