package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.JarReader
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_LICENCE_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_VENDOR_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_VERSION_ATTRIBUTE
import net.corda.libs.packaging.certSummaryHash
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.verify.internal.firstOrThrow
import net.corda.libs.packaging.verify.internal.requireAttribute
import net.corda.libs.packaging.verify.internal.requireAttributeValueIn

/**
 * Verifies CPK format 2.0
 */
class CpkV2Verifier(jarReader: JarReader): CpkVerifier {
    private val name = jarReader.jarName
    private val manifest = jarReader.manifest
    private val codeSigners = jarReader.codeSigners
    override val dependencies: CpkDependencies
    override val id: CpkIdentifier
        get() {
            val certificates = codeSigners.map { it.signerCertPath.certificates.first() }.toSet()
            val cpkSummaryHash = certificates.asSequence().certSummaryHash()
            with (manifest.mainAttributes) {
                return CpkIdentifier(getValue(CPK_NAME_ATTRIBUTE), getValue(CPK_VERSION_ATTRIBUTE), cpkSummaryHash)
            }
        }

    init {
        dependencies = jarReader.entries.filter{ it.name == CPK_DEPENDENCIES_FILE_ENTRY }
            .map { CpkDependencies(it.name, it.createInputStream(), codeSigners) }
            .firstOrThrow(PackagingException("$CPK_DEPENDENCIES_FILE_ENTRY not found in CPK main bundle \"$name\""))
    }

    private fun verifyManifest() {
        with (manifest) {
            requireAttributeValueIn(CPK_FORMAT_ATTRIBUTE, "2.0")
            requireAttribute(CPK_NAME_ATTRIBUTE)
            requireAttribute(CPK_VERSION_ATTRIBUTE)
            requireAttribute(CPK_LICENCE_ATTRIBUTE)
            requireAttribute(CPK_VENDOR_ATTRIBUTE)
        }
    }

    override fun verify() {
        verifyManifest()
    }
}