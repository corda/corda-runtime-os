package net.corda.test.util

import jdk.security.jarsigner.JarSigner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.KeyStore
import java.util.Collections.enumeration
import java.util.Enumeration
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * [ZipFile] that holds content in memory, used for testing purposes. This class was created because [JarSigner] can
 * work only with [ZipFile].
 * Note that it overrides only methods that are used by [JarSigner], and it's not safe to use other methods.
 */
class InMemoryZipFile(fileBytes: ByteArray): ZipFile(DUMMY_FILE) {
    companion object {
        /** Workaround needed as some [ZipFile] functionality cannot be overridden (due to private/final methods) */
        private val DUMMY_FILE: File = Files.createTempFile(null, null).toFile().apply {
            ZipOutputStream(FileOutputStream(this)).close()
            deleteOnExit()
        }
        private val MANIFEST = "META-INF/MANIFEST.MF"
    }
    private val entries = mutableListOf<Entry>()
    constructor(baos: ByteArrayOutputStream) : this(baos.toByteArray())
    constructor(inputStream: InputStream) : this(inputStream.use { it.readAllBytes() })
    constructor() : this(byteArrayOf())

    private class Entry(val entry: ZipEntry, val content: ByteArray) {
        fun createInputStream(): InputStream = ByteArrayInputStream(content)
    }

    init {
        ZipInputStream(ByteArrayInputStream(fileBytes)).use {
            while (true) {
                val zipEntry = it.nextEntry ?: break
                val entry = Entry(zipEntry, it.readAllBytes())
                entries.add(entry)
            }
        }
    }

    override fun entries(): Enumeration<ZipEntry> =
        enumeration(entries.map { it.entry })

    override fun getEntry(name: String): ZipEntry =
        entries.filter { it.entry.name == name }.map { it.entry }.first()

    override fun getInputStream(entry: ZipEntry): InputStream =
        entries.filter { it.entry == entry }.map { it.createInputStream() }.first()

    fun getManifest(): Manifest {
        val manifestEntry = entries[0]
        if (manifestEntry.entry.name != MANIFEST)
            throw java.lang.IllegalStateException("Manifest not found")
        return Manifest().apply { read(manifestEntry.createInputStream()) }
    }

    fun setManifest(manifest: Manifest) {
        // Updating only content won't update entry's metadata
        if (entries.isNotEmpty() && entries[0].entry.name == MANIFEST)
            entries.removeAt(0)
        val newEntry = ZipEntry(MANIFEST)
        val newContent = ByteArrayOutputStream().apply { manifest.write(this) }.toByteArray()
        entries.add(0, Entry(newEntry, newContent))
    }

    fun addEntry(name: String, content: ByteArray) {
        val zipEntry = ZipEntry(name)
        entries.add(Entry(zipEntry, content))
    }

    fun updateEntry(name: String, content: ByteArray) {
        val entryIterator = entries.listIterator()
        while (entryIterator.hasNext()) {
            if (entryIterator.next().entry.name == name) {
                // Updating only content won't update entry's metadata
                entryIterator.remove()
                entryIterator.add(Entry(ZipEntry(name), content))
                return
            }
        }
        throw kotlin.NoSuchElementException("Entry \"$name\" not found")
    }

    fun deleteEntry(name: String) {
        val entry = entries.first { it.entry.name == name }
        entries.remove(entry)
    }

    fun sign(privateKeyEntry: KeyStore.PrivateKeyEntry, signerName: String): InMemoryZipFile {
        val signer = JarSigner.Builder(privateKeyEntry)
            .signerName(signerName)
            .digestAlgorithm("SHA-256")
            .signatureAlgorithm("SHA256withECDSA")
            .build()
        val signedZipFile = ByteArrayOutputStream()
        signer.sign(this, signedZipFile)
        return InMemoryZipFile(signedZipFile)
    }

    fun write(outputStream: OutputStream) {
        ZipOutputStream(outputStream).use {
            entries.forEach { entry ->
                it.putNextEntry(entry.entry)
                it.write(entry.content)
            }
        }
    }

    fun toByteArray(): ByteArray =
        ByteArrayOutputStream().apply { write(this) }.toByteArray()

    fun inputStream(): InputStream =
        ByteArrayInputStream(toByteArray())
}
