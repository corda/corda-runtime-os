package net.corda.libs.packaging.verify

import net.corda.libs.packaging.UncloseableInputStream
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import java.io.InputStream
import java.security.CodeSigner
import java.security.cert.Certificate
import java.util.Arrays
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.Manifest

/**
 * JAR verifier that performs following checks:
 * * All files are signed
 * * Every file within JAR has the same set of signers
 * * JAR signatures are valid
 * * Signatures lead to trusted certificate
 * * Files were not added to JAR
 * * Files were not modified
 * * Files were not deleted
 *
 * @param inputStream Input stream for reading JAR
 * @param trustedCerts Trusted certificates
 * @param location JAR's location that will be used in log and error messages
 */
abstract class JarVerifier(
    private val inputStream: InputStream,
    protected val trustedCerts: Collection<Certificate>,
    protected val location: String?
) {
    companion object {
        /**
         * @see <https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File>
         * Additionally accepting *.EC as its valid for [java.util.jar.JarVerifier] and jarsigner @see
         * https://docs.oracle.com/javase/8/docs/technotes/tools/windows/jarsigner.html, temporally treating
         * META-INF/INDEX.LIST as unsignable entry because [java.util.jar.JarVerifier] doesn't load its signers.
         */
        private val unsignableEntryName = "META-INF/(?:(?:.*[.](?:SF|DSA|RSA|EC)|SIG-.*)|INDEX\\.LIST)".toRegex()

        /**
         * @return if the [entry] [JarEntry] can be signed.
         */
        @JvmStatic
        fun isSignable(entry: JarEntry): Boolean = !entry.isDirectory && !unsignableEntryName.matches(entry.name.uppercase())

        private fun signers2OrderedPublicKeys(codeSigners: Array<CodeSigner>) =
            codeSigners.mapTo(LinkedHashSet()) { (it.signerCertPath.certificates[0]).publicKey }
    }

    /** Manifest */
    private lateinit var manifest: Manifest
    /** Code signers */
    protected var codeSigners: Array<CodeSigner>? = null
    /** Name of the first file with code signers */
    private lateinit var codeSignersFileName: String
    /** Set of all files listed in Manifest */
    private val fileNames: MutableSet<String> = mutableSetOf()
    /** Buffer for reading JAR [InputStream] */
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    /** File location appender */
    protected val fileLocationAppender =
        if (location == null) {
            { msg: String -> msg }
        } else {
            { msg: String -> "$msg in CPK at $location" }
        }

    /**
     * Subclass specific processing of [Manifest]
     * @param manifest Manifest
     */
    abstract fun doOnManifest(manifest: Manifest)

    /**
     * Subclass specific processing of [JarEntry]
     * @param jarEntry JAR entry
     * @param entryInputStream JAR entry input stream
     */
    abstract fun doOnJarEntry(jarEntry: JarEntry, entryInputStream: InputStream)

    /**
     * Subclass specific processing after all JAR entries are processed
     */
    abstract fun doFinal()

    /**
     * Reads JAR file and verifies it
     */
    fun verify() {
        JarInputStream(inputStream, true).use { jarInputStream->
            manifest = jarInputStream.manifest ?: throw CordappManifestException(
                fileLocationAppender("Manifest file is missing or is not the first entry"))
            processManifest(manifest)
            while (true) {
                val jarEntry = jarInputStream.nextJarEntry ?: break
                processEntry(jarEntry, jarInputStream)
            }
            checkForDeletedFiles()
        }
        doFinal()
    }

    /**
     * Processes [Manifest] and calls subclass' handler
     */
    private fun processManifest(manifest: Manifest) {
        fileNames.addAll(manifest.entries.keys)
        doOnManifest(manifest)
    }

    /**
     * Processes [JarEntry] and calls subclass' handler
     */
    private fun processEntry(jarEntry: JarEntry, jarInputStream: JarInputStream) {
        if (jarEntry.isDirectory) return
        val name = jarEntry.name
        if (!isSigningRelated(name)) {
            checkForAddedFile(name)
            doOnJarEntry(jarEntry, UncloseableInputStream(jarInputStream))
            consumeStream(jarInputStream)
            jarInputStream.closeEntry()
            verifySignature(jarEntry)
        }
    }

    /**
     * Consumes given [inputStream]
     */
    protected fun consumeStream(inputStream: InputStream) {
        while (inputStream.read(buffer) != -1) {
            // we just read. this will throw a SecurityException
            // if  a signature/digest check fails.
        }
    }

    /**
     * Verifies that file was not added after JAR was signed
     */
    private fun checkForAddedFile(name: String) {
        if (!fileNames.remove(name)) {
            throw SecurityException(fileLocationAppender("File $name not listed in manifest"))
        }
    }

    /**
     * Verifies that [JarEntry] is signed by a trusted signer and has the same set of code signers as other files
     */
    @Suppress("ThrowsCount")
    private fun verifySignature(jarEntry: JarEntry) {
        if (isSignable(jarEntry)) {
            val certificates = jarEntry.certificates ?: throw InvalidSignatureException(
                fileLocationAppender("File ${jarEntry.name} is not signed"))

            if (!certificates.any { trustedCerts.contains(it) }) throw InvalidSignatureException(
                fileLocationAppender("File ${jarEntry.name} is not signed by a trusted signer"))

            val entrySigners = jarEntry.codeSigners?: throw InvalidSignatureException(
                fileLocationAppender("File ${jarEntry.name} is not signed"))

            if (codeSigners == null) {
                codeSigners = jarEntry.codeSigners
                codeSignersFileName = jarEntry.name
            } else if (!Arrays.equals(codeSigners, entrySigners)) {
                throw InvalidSignatureException(fileLocationAppender(
                    """
                    Mismatch between signers ${signers2OrderedPublicKeys(codeSigners!!)} for file $codeSignersFileName
                    and signers ${signers2OrderedPublicKeys(entrySigners)} for file ${jarEntry.name}
                    """.trimIndent().replace('\n', ' ')))
            }
        }
    }

    /**
     *Verify that file was not dleted after JAR was signed
     */
    private fun checkForDeletedFiles() {
        if (fileNames.isNotEmpty()) {
            throw SecurityException(
                fileLocationAppender("Manifest entry found for missing file ${fileNames.first()}"))
        }
    }

    /**
     * Check whether JAR entry is signing related
     */
    private fun isSigningRelated(name: String): Boolean {
        var uppercaseName = name.uppercase()
        if (!uppercaseName.startsWith("META-INF/"))
            return false

        // Discard "META-INF/" prefix
        uppercaseName = uppercaseName.substring(9)
        if (uppercaseName.indexOf('/') != -1)
            return false

        if (isBlockOrSF(uppercaseName) || (uppercaseName == "MANIFEST.MF"))
            return true

        if (uppercaseName.startsWith("SIG-")) {
            // check filename extension
            // see http://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html#Digital_Signatures
            // for what filename extensions are legal
            extension(uppercaseName)?.let {
                if (!extensionValid(it))
                    return false
            }
            return true // no extension is OK
        }
        return false
    }

    /**
     * Returns file's extension
     */
    private fun extension(fileName: String): String? {
        val extIndex = fileName.lastIndexOf('.')
        return if (extIndex != -1) {
            fileName.substring(extIndex + 1)
        } else {
            null
        }
    }

    /**
     * Checks whether file extension is valid
     */
    private fun extensionValid(extension: String): Boolean =
        extension.isNotEmpty() && extension.length > 3 && extension.all { it.isLetterOrDigit() }

    /**
     * Checks whether file is a signing file or signing block file
     */
    private fun isBlockOrSF(s: String): Boolean {
        return (s.endsWith(".SF")
                || s.endsWith(".DSA")
                || s.endsWith(".RSA")
                || s.endsWith(".EC"))
    }
}