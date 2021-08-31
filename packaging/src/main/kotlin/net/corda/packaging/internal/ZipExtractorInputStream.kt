package net.corda.packaging.internal

import net.corda.packaging.PackagingException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Input stream that extract a zip archive in the provided [destination] while reading it
 */
class ZipExtractorInputStream(source : InputStream,
                              private val destination : Path) : ZipInputStream(source) {
    private var currentFile : OutputStream? = null
    override fun getNextEntry(): ZipEntry? {
        return super.getNextEntry()?.also { entry ->
            val newFileSystemLocation = destination.resolve(entry.name)
            if(entry.isDirectory) {
                Files.createDirectories(newFileSystemLocation)
            } else {
                Files.createDirectories(newFileSystemLocation.parent)
                currentFile = Files.newOutputStream(newFileSystemLocation)
            }
        }
    }

    override fun read(): Int {
        val result = super.read()
        if(result != -1) currentFile?.write(result)
        return result
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)
        if(read != -1) currentFile?.write(b, off, read)
        return read
    }

    override fun closeEntry() {
        super.closeEntry()
        currentFile?.close()
    }
}

/**
 * Input stream that extract a jar archive in the provided [destination] while reading it
 */
class JarExtractorInputStream(source : InputStream,
                              private val destination : Path,
                              verify : Boolean, private val sourceLocation : String?) : JarInputStream(source, verify) {
    private var currentFile : OutputStream? = null

    init {
        val newFileSystemLocation = destination.resolve(JarFile.MANIFEST_NAME)
        Files.createDirectories(newFileSystemLocation.parent)
        Files.newOutputStream(newFileSystemLocation).use { outputStream ->
            (manifest ?: throw PackagingException(
                    "The source stream ${sourceLocation?.let {"from '$it'"} ?: ""} doesn't represent a valid jar file"))
                    .write(outputStream)
        }
    }

    override fun getNextEntry(): ZipEntry? {
        return super.getNextEntry()?.also { entry ->
            val newFileSystemLocation = destination.resolve(entry.name)
            if(entry.isDirectory) {
                Files.createDirectories(newFileSystemLocation)
            } else {
                Files.createDirectories(newFileSystemLocation.parent)
                currentFile = Files.newOutputStream(newFileSystemLocation)
            }
        }
    }

    override fun read(): Int {
        val result = super.read()
        if(result != -1) currentFile?.write(result)
        return result
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)
        if(read != -1) currentFile?.write(b, off, read)
        return read
    }

    override fun closeEntry() {
        super.closeEntry()
        currentFile?.close()
    }
}