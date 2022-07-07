package net.corda.libs.packaging.verify

import net.corda.libs.packaging.core.exception.CordappManifestException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.CodeSigner
import java.security.cert.X509Certificate
import java.util.jar.JarInputStream
import java.util.jar.Manifest

/**
 * Stores JAR in memory, validates it and allows access to [Manifest] and [Entry]s.
 * Validation checks that JAR was not tampered and that signatures lead to [trustedCerts].
 */
internal class JarReader private constructor(val jarName: String, jarBytes: ByteArray, val trustedCerts: Collection<X509Certificate>) {
    val manifest: Manifest
    val entries: List<Entry>
    val codeSigners: List<CodeSigner>

    constructor(jarName: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate>) :
        this(jarName, inputStream.readAllBytesAndClose(), trustedCerts)

    inner class Entry(val name: String, private val entryBytes: ByteArray) {
        /** Creates [InputStream] for reading this entry */
        fun createInputStream(): InputStream = ByteArrayInputStream(entryBytes)

        /** Creates [JarReader] for reading this JAR entry. Created [JarReader] uses memory storage of this [JarReader] */
        fun createJarReader(): JarReader = JarReader("$jarName/$name", entryBytes, trustedCerts)
    }

    init {
        val jarVerifier = JarVerifier(jarName, ByteArrayInputStream(jarBytes), trustedCerts)
        jarVerifier.verify()
        codeSigners = jarVerifier.codeSigners

        val jarEntries: MutableList<Entry> = mutableListOf()
        JarInputStream(ByteArrayInputStream(jarBytes), false).use {
            manifest = it.manifest
                ?: throw CordappManifestException("Manifest file is missing or is not the first entry in package \"$jarName\"")

            while (true) {
                val jarEntry = it.nextJarEntry ?: break
                jarEntries.add(Entry(jarEntry.name, it.readAllBytes()))
            }
        }
        entries = jarEntries
    }
}

private fun InputStream.readAllBytesAndClose(): ByteArray {
    return this.use {
        it.readAllBytes()
    }
}
