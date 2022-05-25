package net.corda.libs.packaging.verify

import net.corda.libs.packaging.CpkDependencyResolver
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.exception.PackagingException
import java.io.InputStream
import java.security.cert.Certificate
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeMap
import java.util.jar.JarEntry
import java.util.jar.Manifest

/**
 * CPB verifier that performs following checks:
 * * All files are signed
 * * Every file within CPK has the same set of signers
 * * CPK signatures are valid
 * * Signatures lead to trusted certificate
 * * Files were not added to CPK
 * * Files were not modified
 * * Files were not deleted
 * * Corda-CPK-Format is 1.0 or 2.0
 * * CPK validation of each CPK
 *
 * @param inputStream Input stream for reading JAR
 * @param trustedCerts Trusted certificates
 * @param location JAR's location that will be used in log and error messages
 */
class CpbVerifier(inputStream: InputStream, trustedCerts: Collection<Certificate>, location: String? = null
): JarVerifier(inputStream, trustedCerts, location) {
    companion object {
        const val CPK_FILE_EXTENSION = ".cpk"

        private const val CORDA_CPB_NAME = "Corda-CPB-Name"
        private const val CORDA_CPB_VERSION = "Corda-CPB-Version"
    }
    /** CPB name */
    private lateinit var cpbName: String
    /** CPB version */
    private lateinit var cpbVersion: String
    /** CPK dependencies */
    private val dependencyMap: NavigableMap<CpkIdentifier, NavigableSet<CpkIdentifier>> = TreeMap()

    override fun doOnManifest(manifest: Manifest) {
        cpbName = manifest.mainAttributes.getValue(CORDA_CPB_NAME) ?: throw PackagingException(
            fileLocationAppender("CPB manifest is missing mandatory attribute $CORDA_CPB_NAME"))
        cpbVersion = manifest.mainAttributes.getValue(CORDA_CPB_VERSION) ?: throw PackagingException(
            fileLocationAppender("CPB manifest is missing mandatory attribute $CORDA_CPB_VERSION"))
    }

    override fun doOnJarEntry(jarEntry: JarEntry, entryInputStream: InputStream) {
        when {
            isCpk(jarEntry) -> {
                val cpkVerifier = CpkVerifier(
                    entryInputStream,
                    trustedCerts,
                    location.plus("/${jarEntry.name}"))
                cpkVerifier.verify()
                dependencyMap[cpkVerifier.cpkId] = cpkVerifier.dependencies
            }
        }
    }

    override fun doFinal() {
        checkCpkDependencies()
    }

    private fun checkCpkDependencies() {
        CpkDependencyResolver.resolveDependencies(dependencyMap.keys, dependencyMap, useSignatures = true)
    }

    private fun isCpk(cpkEntry: JarEntry): Boolean {
        return !cpkEntry.isDirectory
                && cpkEntry.name.let {
            it.indexOf('/') == -1 && it.lowercase().endsWith(CPK_FILE_EXTENSION)
        }
    }
}