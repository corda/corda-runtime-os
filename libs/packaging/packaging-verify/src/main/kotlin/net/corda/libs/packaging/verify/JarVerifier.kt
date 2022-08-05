package net.corda.libs.packaging.verify

import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import net.corda.libs.packaging.signerInfo
import java.io.InputStream
import java.security.CodeSigner
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import net.corda.libs.packaging.verify.SigningHelpers.isSigningRelated

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
                if (jarEntry.isDirectory || isSigningRelated(jarEntry)) continue

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
}