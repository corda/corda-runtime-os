package net.corda.libs.packaging.verify

import net.corda.libs.packaging.CpkDocumentReader
import net.corda.libs.packaging.CpkDocumentReader.Companion.SAME_SIGNER_PLACEHOLDER
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY
import net.corda.libs.packaging.certSummaryHash
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.security.cert.Certificate
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeSet
import java.util.jar.JarEntry
import java.util.jar.Manifest

/**
* CPK main bundle verifier that performs following checks:
* * All files are signed
* * Every file within CPK has the same set of signers
* * CPK signatures are valid
* * Signatures lead to trusted certificate
* * Files were not added to CPK
* * Files were not modified
* * Files were not deleted
*
* @param inputStream Input stream for reading JAR
* @param trustedCerts Trusted certificates
* @param location JAR's location that will be used in log and error messages
*/
class CpkMainBundleVerifier (inputStream: InputStream, trustedCerts: Collection<Certificate>, location: String?
) : JarVerifier(inputStream, trustedCerts, location) {
    /** CPK summary hash */
    private var cpkSummaryHash: SecureHash? = null
    /** CPKs this CPK depends on */
    private var cpkDependencies: NavigableSet<CpkIdentifier> = Collections.emptyNavigableSet()
    /** CPK library constraints */
    private var libraryConstraints: NavigableMap<String, SecureHash> = Collections.emptyNavigableMap()
    /** CPK summary hash */
    val summaryHash: SecureHash
        get() = cpkSummaryHash!!
    /** CPKs this CPK depends on */
    val dependencies: NavigableSet<CpkIdentifier>
        get() = Collections.unmodifiableNavigableSet(cpkDependencies)

    /**
     * Returns hash for given library
     */
    fun getLibraryHash(name: String) = libraryConstraints[name]

    override fun doOnManifest(manifest: Manifest) {
        // nothing to do
    }

    override fun doOnJarEntry(jarEntry: JarEntry, entryInputStream: InputStream) {
        when (jarEntry.name) {
            CPK_DEPENDENCIES_FILE_ENTRY ->
                cpkDependencies = CpkDocumentReader.readDependencies(jarEntry.name, entryInputStream, fileLocationAppender)
            CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY ->
                libraryConstraints = CpkDocumentReader.readLibraryConstraints(jarEntry.name, entryInputStream, fileLocationAppender)
        }
    }

    override fun doFinal() {
        // Replace any "same as me" placeholders with this CPK's actual summary hash.
        val certificates = codeSigners!!.map { it.signerCertPath.certificates.first() }.toSet()
        cpkSummaryHash = certificates.asSequence().certSummaryHash()
        cpkDependencies = cpkDependencies.mapTo(TreeSet()) { cpk ->
            if (cpk.signerSummaryHash === SAME_SIGNER_PLACEHOLDER) {
                CpkIdentifier(cpk.name, cpk.version, cpkSummaryHash)
            } else {
                cpk
            }
        }
    }
}