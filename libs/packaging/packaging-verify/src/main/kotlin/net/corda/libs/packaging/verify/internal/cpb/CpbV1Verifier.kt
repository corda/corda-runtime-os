package net.corda.libs.packaging.verify.internal.cpb

import net.corda.libs.packaging.CpkDependencyResolver
import net.corda.libs.packaging.verify.JarReader
import net.corda.libs.packaging.PackagingConstants.CPB_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPB_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPB_VERSION_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_FILE_EXTENSION
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.verify.internal.cpk.CpkV1Verifier
import net.corda.libs.packaging.verify.internal.requireAttribute
import net.corda.libs.packaging.verify.internal.requireAttributeValueIn
import java.util.TreeMap
import java.util.jar.Manifest

/**
 * Verifies CPB format 1.0
 */
class CpbV1Verifier internal constructor(private val packageType: String, jarReader: JarReader): CpbVerifier {
    private val name = jarReader.jarName
    private val manifest: Manifest = jarReader.manifest
    private val cpkVerifiers: List<CpkV1Verifier>

    constructor (jarReader: JarReader): this("CPB", jarReader)

    init {
        cpkVerifiers = jarReader.entries.filter(::isCpk).map {
            CpkV1Verifier(JarReader("$name/${it.name}", it.createInputStream(), jarReader.trustedCerts))
        }
    }

    private fun isCpk(entry: JarReader.Entry): Boolean {
        return entry.name.let {
            it.indexOf('/') == -1 &&
            it.endsWith(CPK_FILE_EXTENSION, ignoreCase = true)
        }
    }

    private fun verifyManifest() {
        with (manifest) {
            requireAttributeValueIn(CPB_FORMAT_ATTRIBUTE, null, "1.0")
            requireAttribute(CPB_NAME_ATTRIBUTE)
            requireAttribute(CPB_VERSION_ATTRIBUTE)
        }
    }

    private fun verifyCpks() {
        if (cpkVerifiers.isEmpty())
            throw PackagingException("None CPK found in $packageType \"$name\"")

        cpkVerifiers.forEach { it.verify() }

        // Verify dependencies
        val dependencyMap = TreeMap(cpkVerifiers.associate { it.id to it.dependencies.dependencies })
        CpkDependencyResolver.resolveDependencies(dependencyMap.keys, dependencyMap, useSignatures = true)
    }

    override fun verify() {
        verifyManifest()
        verifyCpks()
    }
}