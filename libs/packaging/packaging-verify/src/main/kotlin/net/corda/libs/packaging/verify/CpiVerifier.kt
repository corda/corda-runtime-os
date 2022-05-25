package net.corda.libs.packaging.verify

import net.corda.libs.packaging.core.exception.PackagingException
import java.io.InputStream
import java.security.cert.Certificate
import java.util.jar.JarEntry
import java.util.jar.Manifest

/**
 * CPI verifier that performs following checks:
 * * All files are signed
 * * Every file within CPK has the same set of signers
 * * CPK signatures are valid
 * * Signatures lead to trusted certificate
 * * Files were not added to CPK
 * * Files were not modified
 * * Files were not deleted
 * * CPB validation
 *
 * @param inputStream Input stream for reading JAR
 * @param trustedCerts Trusted certificates
 * @param location JAR's location that will be used in log and error messages
 */
class CpiVerifier(inputStream: InputStream, trustedCerts: Collection<Certificate>, location: String? = null
): JarVerifier(inputStream, trustedCerts, location) {
    companion object {
        const val CPB_FILE_EXTENSION = ".cpb"
        private const val CPI_GROUP_POLICY = "META-INF/GroupPolicy.json"
        private const val CORDA_CPI_FORMAT = "Corda-CPI-Format"
        private const val CORDA_CPI_NAME = "Corda-CPI-Name"
        private const val CORDA_CPI_VERSION = "Corda-CPI-Version"
        private val CPI_FORMATS = setOf("1.0", "2.0")
    }

    /** CPI format */
    private lateinit var cpiFormat: String
    /** CPI name */
    private lateinit var cpiName: String
    /** CPI version */
    private lateinit var cpiVersion: String
    private var hasGroupPolicy = false

    override fun doOnManifest(manifest: Manifest) {
        // Check CPI format
        cpiFormat = manifest.mainAttributes.getValue(CORDA_CPI_FORMAT)
        if (!CPI_FORMATS.contains(cpiFormat)) {
            throw PackagingException(
                fileLocationAppender("CPI manifest has invalid $CORDA_CPI_FORMAT attribute value: $cpiFormat"))
        }
        // Check ID attributes
        cpiName = manifest.mainAttributes.getValue(CORDA_CPI_NAME) ?: throw PackagingException(
            fileLocationAppender("CPB manifest is missing mandatory attribute $CORDA_CPI_NAME"))
        cpiVersion = manifest.mainAttributes.getValue(CORDA_CPI_VERSION) ?: throw PackagingException(
            fileLocationAppender("CPB manifest is missing mandatory attribute $CORDA_CPI_VERSION"))
    }

    override fun doOnJarEntry(jarEntry: JarEntry, entryInputStream: InputStream) {
        when {
            isCpb(jarEntry) -> {
                val cpbVerifier = CpbVerifier(
                    entryInputStream,
                    trustedCerts,
                    location.plus("/${jarEntry.name}"))
                cpbVerifier.verify()
            }
            isGroupPolicy(jarEntry) -> hasGroupPolicy = true
        }
    }

    override fun doFinal() {
        if (!hasGroupPolicy) {
            throw PackagingException(fileLocationAppender("Group policy file is missing"))
        }
    }

    private fun isCpb(cpiEntry: JarEntry): Boolean {
        return !cpiEntry.isDirectory
                && cpiEntry.name.let {
            it.indexOf('/') == -1 && it.lowercase().endsWith(CPB_FILE_EXTENSION)
        }
    }

    private fun isGroupPolicy(cpiEntry: JarEntry): Boolean =
        cpiEntry.name.equals(CPI_GROUP_POLICY, ignoreCase = true)
}