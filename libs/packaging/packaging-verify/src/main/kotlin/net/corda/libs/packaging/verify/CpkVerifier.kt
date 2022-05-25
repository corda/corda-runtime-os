package net.corda.libs.packaging.verify

import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.JAR_FILE_EXTENSION
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.exception.LibraryIntegrityException
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeMap
import java.util.jar.JarEntry
import java.util.jar.Manifest
import java.util.zip.ZipEntry

/**
 * CPK verifier that performs following checks:
 * * All files are signed
 * * Every file within CPK has the same set of signers
 * * CPK signatures are valid
 * * Signatures lead to trusted certificate
 * * Files were not added to CPK
 * * Files were not modified
 * * Files were not deleted
 * * Corda-CPK-Format is 1.0 or 2.0
 *
 * @param inputStream Input stream for reading JAR
 * @param trustedCerts Trusted certificates
 * @param location JAR's location that will be used in log and error messages
 */
class CpkVerifier(inputStream: InputStream, trustedCerts: Collection<Certificate>, location: String? = null
): JarVerifier(inputStream, trustedCerts, location) {
    companion object {
        private const val CPK_LIB_FOLDER = "lib"
        private const val CORDA_CPK_FORMAT = "Corda-CPK-Format"
        private const val CPK_FORMAT_V1 = "1.0"
        private const val CPK_FORMAT_V2 = "2.0"
        private const val CORDA_CPK_CORDAPP_NAME = "Corda-CPK-Cordapp-Name"
        private const val CORDA_CPK_CORDAPP_VERSION = "Corda-CPK-Cordapp-Version"
        private val CPK_FORMATS = setOf(CPK_FORMAT_V1, CPK_FORMAT_V2)
    }

    /** CPK format */
    private lateinit var cpkFormat: String
    /** CPK name */
    private lateinit var cpkName: String
    /** CPK version */
    private lateinit var cpkVersion: String
    /** CPK main bundle verifier */
    private lateinit var cpkMainBundleVerifier: CpkMainBundleVerifier
    /** Number of top lever JARs */
    private var topLevelJars: Int = 0
    /** Map of libraries */
    private val libraryMap: NavigableMap<String, SecureHash> = TreeMap()
    /** CPK Identifier */
    val cpkId: CpkIdentifier
        get() = CpkIdentifier(cpkName, cpkVersion, cpkMainBundleVerifier.summaryHash)
    /** CPKs this CPK depends on */
    val dependencies: NavigableSet<CpkIdentifier>
        get() = cpkMainBundleVerifier.dependencies

    override fun doOnManifest(manifest: Manifest) {
        // Check CPK format
        cpkFormat = manifest.mainAttributes.getValue(CORDA_CPK_FORMAT)
        if (!CPK_FORMATS.contains(cpkFormat)) {
            throw PackagingException(
                fileLocationAppender("CPK manifest has invalid $CORDA_CPK_FORMAT attribute value: $cpkFormat"))
        }
        // Get ID attributes
        cpkName = manifest.mainAttributes.getValue(CORDA_CPK_CORDAPP_NAME) ?: throw PackagingException(
            fileLocationAppender("CPK manifest is missing $CORDA_CPK_CORDAPP_NAME attribute value"))
        cpkVersion = manifest.mainAttributes.getValue(CORDA_CPK_CORDAPP_VERSION) ?: throw PackagingException(
            fileLocationAppender("CPK manifest is missing $CORDA_CPK_CORDAPP_VERSION attribute value"))
    }

    override fun doOnJarEntry(jarEntry: JarEntry, entryInputStream: InputStream) {
        if (cpkFormat1()) {
            when {
                isMainJar(jarEntry) -> processMainJar(entryInputStream, jarEntry)
                isLibJar(jarEntry) -> processLibJar(entryInputStream, jarEntry)
            }
        }
    }

    @Suppress("ThrowsCount")
    override fun doFinal() {
        // Check there was only one main JAR
        when {
            topLevelJars == 0 -> throw PackagingException(fileLocationAppender("No CorDapp JAR found"))
            topLevelJars > 1 -> throw PackagingException(fileLocationAppender("$topLevelJars CorDapp JARs found"))
        }

        // Check library dependecy constraints
        libraryMap.forEach { (entryName, hash) ->
            val requiredHash = cpkMainBundleVerifier.getLibraryHash(entryName) ?: throw PackagingException(
                fileLocationAppender("$entryName not found in $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY")
            )
            if (hash != requiredHash) {
                throw LibraryIntegrityException(
                    fileLocationAppender(
                        "Hash of '$entryName' differs from the content of $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY"
                    )
                )
            }
        }
    }

    /** Process main JAR */
    private fun processMainJar(inputStream: InputStream, cpkEntry: JarEntry) {
        cpkMainBundleVerifier = CpkMainBundleVerifier(
            inputStream,
            trustedCerts,
            location.plus("/${cpkEntry.name}"))
        cpkMainBundleVerifier.verify()
        topLevelJars++
    }

    /** process library JAR */
    private fun processLibJar(inputStream: InputStream, cpkEntry: JarEntry) {
        val digestBytes = try {
            val libraryMessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
            consumeStream(DigestInputStream(inputStream, libraryMessageDigest))
            libraryMessageDigest.digest()
        } catch (e: IOException) {
            throw PackagingException(
                fileLocationAppender("Could not calculate hash of library jar '${cpkEntry.name}"),
                e
            )
        }
        libraryMap[cpkEntry.name] = SecureHash(DigestAlgorithmName.SHA2_256.name, digestBytes)
    }

    /** Checks whether [JarEntry] is a main JAR */
    private fun isMainJar(cpkEntry: JarEntry): Boolean {
        return !cpkEntry.isDirectory
                && cpkEntry.name.let {
            it.indexOf('/') == -1 && it.lowercase().endsWith(JAR_FILE_EXTENSION)
        }
    }

    /** Checks whether [JarEntry] is a library JAR */
    private fun isLibJar(jarEntry: ZipEntry): Boolean {
        return !jarEntry.isDirectory
                && jarEntry.name.let {
            it.startsWith(CPK_LIB_FOLDER) && it.indexOf('/') == CPK_LIB_FOLDER.length &&
                    it.indexOf('/', CPK_LIB_FOLDER.length + 1) == -1 && it.endsWith(JAR_FILE_EXTENSION)
        }
    }

    /** Checks whether CPK format is 1.0 */
    private fun cpkFormat1() = cpkFormat == CPK_FORMAT_V1
}