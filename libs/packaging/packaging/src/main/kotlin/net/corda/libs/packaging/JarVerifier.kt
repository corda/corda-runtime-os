package net.corda.libs.packaging

import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import java.io.InputStream
import java.security.CodeSigner
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.Manifest

/**
 * Verifies JAR by performing following checks:
 * * JAR signatures are valid
 * * Signatures lead to trusted certificate
 * * All files are signed
 * * Every file within JAR has the same set of signers
 * * Files were not modified
 * * Files were not added after it was initially signed
 * * Files were not deleted after it was initially signed
 *
 * @param jarName JAR's name/location that will be used in log and error messages
 * @param inputStream Input stream for reading JAR
 * @param trustedCerts Trusted certificates
 */
class JarVerifier(
    val jarName: String,
    private val inputStream: InputStream,
    private val trustedCerts: Collection<X509Certificate>
) {
    private var manifest: Manifest? = null
    private val jarEntries: MutableList<JarEntry> = mutableListOf()
    var codeSigners: List<CodeSigner> = emptyList()

    fun verify() {
        verifySignatures()
        verifyCertificates(codeSigners, trustedCerts)
        verifyNoFilesAdded()
        verifyNoFilesDeleted()
    }

    /** Verifies JAR's signature using functionality provided by [JarInputStream] */
    @Suppress("ComplexMethod", "ThrowsCount")
    private fun verifySignatures() {
        JarInputStream(inputStream, true).use {
            // Manifest has to be present in order to verify the signatures
            manifest = it.manifest
                ?: throw CordappManifestException("Manifest file is missing or is not the first entry in package \"$jarName\"")

            // All code signers have to be the same (match those of the first signed entry)
            var firstSignedEntry: JarEntry? = null

            while (true) {
                // Invalid signature is detected while positioning the stream for the next entry
                // and will result with SecurityException
                val jarEntry = it.nextJarEntry ?: break

                // All files except signing related files should be signed
                if (jarEntry.isDirectory || isSigningRelated(jarEntry.name)) continue

                // Code signers can be retrieved only after JarEntry has been completely verified by reading from the entry
                // input stream until the end of the stream has been reached
                it.closeEntry()
                if (jarEntry.codeSigners == null)
                    throw InvalidSignatureException("File ${jarEntry.name} is not signed in package \"$jarName\"")

                // All signed files should have the same signers
                if (firstSignedEntry == null) {
                    firstSignedEntry = jarEntry
                } else if (!Arrays.equals(firstSignedEntry.codeSigners, jarEntry.codeSigners)) {
                    throw InvalidSignatureException(
                        "Mismatch between signers ${signerInfo(firstSignedEntry)} for file ${firstSignedEntry.name}" +
                                " and signers ${signerInfo(jarEntry)} for file ${jarEntry.name} in package \"$jarName\""
                    )
                }

                jarEntries.add(jarEntry)
            }

            if (firstSignedEntry == null)
                throw InvalidSignatureException("No signed files found in package \"$jarName\"")

            codeSigners = firstSignedEntry.codeSigners.toList()
        }
    }

    /** Verifies that no file was added after JAR was signed */
    private fun verifyNoFilesAdded() {
        val filesInManifest = manifest!!.entries.keys
        jarEntries.forEach {
            if (!filesInManifest.contains(it.name))
                throw SecurityException("File ${it.name} not listed in manifest in package \"$jarName\"")
        }
    }

    /** Verifies that no file was deleted after JAR was signed */
    private fun verifyNoFilesDeleted() {
        val filesInManifest = manifest!!.entries.keys.toMutableSet()
        jarEntries.forEach {
            filesInManifest.remove(it.name)
        }
        if (filesInManifest.isNotEmpty()) {
            throw SecurityException("Manifest entry found for missing file ${filesInManifest.first()} in package \"$jarName\"")
        }
    }

    /** Check whether JAR entry is signing related */
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
            extension(uppercaseName)?.let {
                if (!extensionValid(it))
                    return false
            }
            return true // no extension is OK
        }
        return false
    }

    /** Returns file's extension */
    private fun extension(fileName: String): String? {
        val extIndex = fileName.lastIndexOf('.')
        return if (extIndex != -1) {
            fileName.substring(extIndex + 1)
        } else {
            null
        }
    }

    /**
     * Checks whether file extension is valid.
     * See http://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html#Digital_Signatures
     * */
    private fun extensionValid(extension: String): Boolean =
        extension.isNotEmpty() && extension.length > 3 && extension.all { it.isLetterOrDigit() }

    /** Checks whether file is a signing file or signing block file */
    private fun isBlockOrSF(s: String): Boolean {
        return (s.endsWith(".SF")
                || s.endsWith(".DSA")
                || s.endsWith(".RSA")
                || s.endsWith(".EC"))
    }
}
