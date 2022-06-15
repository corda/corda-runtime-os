package net.corda.libs.packaging.verify.internal.cpb

import net.corda.libs.packaging.CpkDependencyResolver
import net.corda.libs.packaging.JarReader
import net.corda.libs.packaging.PackagingConstants.CPB_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPB_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPB_VERSION_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPI_GROUP_POLICY_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_FILE_EXTENSION
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.verify.internal.VerifierFactory
import net.corda.libs.packaging.verify.internal.VerifierFactory.FORMAT_1
import net.corda.libs.packaging.verify.internal.cpi.GroupPolicy
import net.corda.libs.packaging.verify.internal.cpk.CpkVerifier
import net.corda.libs.packaging.verify.internal.firstOrThrow
import net.corda.libs.packaging.verify.internal.requireAttribute
import net.corda.libs.packaging.verify.internal.requireAttributeValueIn
import java.util.TreeMap
import java.util.jar.Manifest

/**
 * Verifies CPB format 1.0
 */
class CpbV1Verifier(jarReader: JarReader): CpbVerifier {
    private val name = jarReader.jarName
    private val manifest: Manifest = jarReader.manifest
    private val cpkVerifiers: List<CpkVerifier>
    private val groupPolicy: GroupPolicy

    init {
        cpkVerifiers = jarReader.entries.filter(::isCpk).map {
            VerifierFactory.createCpkVerifier(FORMAT_1, "$name/${it.name}", it.createInputStream(), jarReader.trustedCerts)
        }
        groupPolicy = jarReader.entries.filter(::isGroupPolicy).map { GroupPolicy() }
            .firstOrThrow(PackagingException("Group policy not found in CPI \"$name\""))
    }

    private fun isCpk(entry: JarReader.Entry): Boolean {
        return entry.name.let {
            it.indexOf('/') == -1 &&
            it.endsWith(CPK_FILE_EXTENSION, ignoreCase = true)
        }
    }

    private fun isGroupPolicy(entry: JarReader.Entry): Boolean =
        entry.name.endsWith(CPI_GROUP_POLICY_ENTRY, ignoreCase = true)

    private fun verifyManifest() {
        with (manifest) {
            requireAttributeValueIn(CPB_FORMAT_ATTRIBUTE, null, "1.0")
            requireAttribute(CPB_NAME_ATTRIBUTE)
            requireAttribute(CPB_VERSION_ATTRIBUTE)
        }
    }

    private fun verifyCpks() {
        if (cpkVerifiers.isEmpty())
            throw PackagingException("None CPK found in CPB \"$name\"")

        cpkVerifiers.forEach { it.verify() }

        // Verify dependencies
        val dependencyMap = TreeMap(cpkVerifiers.associate { it.id to it.dependencies.dependencies })
        CpkDependencyResolver.resolveDependencies(dependencyMap.keys, dependencyMap, useSignatures = true)
    }

    override fun verify() {
        verifyManifest()
        verifyCpks()
        groupPolicy.verify()
    }
}