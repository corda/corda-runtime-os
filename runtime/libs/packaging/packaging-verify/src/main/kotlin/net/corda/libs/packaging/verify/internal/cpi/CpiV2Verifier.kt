package net.corda.libs.packaging.verify.internal.cpi

import net.corda.libs.packaging.verify.JarReader
import net.corda.libs.packaging.PackagingConstants.CPB_FILE_EXTENSION
import net.corda.libs.packaging.PackagingConstants.CPI_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPI_GROUP_POLICY_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPI_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPI_VERSION_ATTRIBUTE
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.verify.internal.cpb.CpbV2Verifier
import net.corda.libs.packaging.verify.internal.firstOrThrow
import net.corda.libs.packaging.verify.internal.requireAttribute
import net.corda.libs.packaging.verify.internal.requireAttributeValueIn
import java.util.jar.Manifest

/**
 * Verifies CPI format 2.0
 */
class CpiV2Verifier(jarReader: JarReader): CpiVerifier {
    private val name = jarReader.jarName
    private val manifest: Manifest = jarReader.manifest
    private val cpbVerifier: CpbV2Verifier?
    private val groupPolicy: GroupPolicy

    init {
        cpbVerifier = jarReader.entries.filter(::isCpb)
            .map { CpbV2Verifier(JarReader("$name/${it.name}", it.createInputStream(), jarReader.trustedCerts)) }
            .let {
                    when (it.size) {
                        0 -> null
                        1 -> it[0]
                        else -> throw PackagingException("Multiple CPBs found in CPI \"$name\"")
                    }
                }

        groupPolicy = jarReader.entries.filter(::isGroupPolicy).map { GroupPolicy() }
            .firstOrThrow(PackagingException("Group policy not found in CPI \"$name\""))
    }

    private fun isCpb(entry: JarReader.Entry): Boolean {
        return entry.name.let {
            it.indexOf('/') == -1 &&
            it.endsWith(CPB_FILE_EXTENSION, ignoreCase = true)
        }
    }

    private fun isGroupPolicy(entry: JarReader.Entry): Boolean =
        entry.name.equals("META-INF/$CPI_GROUP_POLICY_ENTRY", ignoreCase = true)

    private fun verifyManifest() {
        with (manifest) {
            requireAttributeValueIn(CPI_FORMAT_ATTRIBUTE, "2.0")
            requireAttribute(CPI_NAME_ATTRIBUTE)
            requireAttribute(CPI_VERSION_ATTRIBUTE)
        }
    }

    override fun verify() {
        verifyManifest()
        cpbVerifier?.verify()
        groupPolicy.verify()
    }
}