package net.corda.packaging.util

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Helper class to ease the creation of a copy of zip archive,
 * editing some of its [ZipEntry]
 */
open class ZipTweaker {

    protected enum class AfterTweakAction {
        WRITE_ORIGINAL_ENTRY, DO_NOTHING
    }

    protected open fun tweakEntry(inputStream : ZipInputStream,
                        outputStream : ZipOutputStream,
                        currentEntry: ZipEntry,
                        buffer : ByteArray) : AfterTweakAction = AfterTweakAction.WRITE_ORIGINAL_ENTRY

    @Suppress("NestedBlockDepth", "ComplexMethod")
    fun run(source : InputStream, destination : OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        ZipInputStream(source).use { inputStream ->
            ZipOutputStream(destination).use { outputStream ->
                while(true) {
                    val zipEntry = inputStream.nextEntry ?: break
                    if(tweakEntry(inputStream, outputStream, zipEntry, buffer) == AfterTweakAction.WRITE_ORIGINAL_ENTRY) {
                        outputStream.putNextEntry(zipEntry)
                        while(true) {
                            val read = inputStream.read(buffer)
                            if(read < 0) break
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private fun computeSizeAndCrc32(
                zipEntry: ZipEntry,
                inputStream: InputStream,
                buffer: ByteArray) {
            val crc32 = CRC32()
            var sz = 0L
            while (true) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                sz += read.toLong()
                crc32.update(buffer, 0, read)
            }
            zipEntry.size = sz
            zipEntry.compressedSize = sz
            zipEntry.crc = crc32.value
        }

        @JvmStatic
        fun write2Stream(outputStream: ZipOutputStream,
                         inputStream: InputStream,
                         buffer: ByteArray) {
            while (true) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                outputStream.write(buffer, 0, read)
            }
        }

        @JvmStatic
        fun writeZipEntry(
                zip: ZipOutputStream,
                source: () -> InputStream,
                destinationFileName: String,
                buffer: ByteArray = ByteArray(DEFAULT_BUFFER_SIZE),
                compressionMethod: Int = ZipEntry.DEFLATED) {
            val zipEntry = ZipEntry(destinationFileName).apply {
                val ze = this
                method = compressionMethod
                when (method) {
                    ZipEntry.STORED -> {
                        // A stored ZipEntry requires computing the size and CRC32 in advance
                        source().use {
                            computeSizeAndCrc32(ze, it, buffer)
                        }
                    }
                    ZipEntry.DEFLATED -> {
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported zip entry compression method value: $compressionMethod")
                    }
                }
            }
            zip.putNextEntry(zipEntry)
            source().use {
                write2Stream(zip, it, buffer)
            }
            zip.closeEntry()
        }

        @JvmStatic
        fun removeJarSignature(jarFile: Path, outFile : Path? = null) {
            val destination = outFile ?: Files.createTempFile(jarFile.parent, jarFile.fileName.toString(), ".tmp")
            @Suppress("TooGenericExceptionCaught")
            try {
                object : ZipTweaker() {
                    override fun tweakEntry(inputStream: ZipInputStream, outputStream: ZipOutputStream, currentEntry: ZipEntry, buffer: ByteArray): AfterTweakAction {
                        return if(currentEntry.name.startsWith("META-INF/") && currentEntry.name.uppercase().endsWith(".SF"))
                            AfterTweakAction.DO_NOTHING
                        else
                            AfterTweakAction.WRITE_ORIGINAL_ENTRY
                    }
                }.run(Files.newInputStream(jarFile), Files.newOutputStream(destination))
                if(outFile == null) {
                    Files.move(destination, jarFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Throwable) {
                destination.takeIf(Files::exists)?.let(Files::delete)
                throw e
            }
        }
    }
}