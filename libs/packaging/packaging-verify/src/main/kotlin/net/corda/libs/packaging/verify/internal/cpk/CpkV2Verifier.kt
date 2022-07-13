package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.verify.JarReader
import net.corda.libs.packaging.PackagingConstants.CPK_BUNDLE_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_BUNDLE_VERSION_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.verify.internal.firstOrThrow
import net.corda.libs.packaging.verify.internal.requireAttribute
import net.corda.libs.packaging.verify.internal.requireAttributeValueIn

/**
 * Verifies CPK format 2.0
 */
class CpkV2Verifier(jarReader: JarReader): CpkVerifier {
    val name = jarReader.jarName
    private val manifest = jarReader.manifest
    val codeSigners = jarReader.codeSigners
    internal val dependencies: List<CpkDependency>

    init {
        val dependenciesEntry = jarReader.entries.filter{ it.name == CPK_DEPENDENCIES_FILE_ENTRY }
            .firstOrThrow(PackagingException("$CPK_DEPENDENCIES_FILE_ENTRY not found in CPK main bundle \"$name\""))
        dependencies = CpkV2DependenciesReader.readDependencies(name, dependenciesEntry.createInputStream(), codeSigners)
    }

    fun bundleName(): String = manifest.mainAttributes.getValue(CPK_BUNDLE_NAME_ATTRIBUTE)
    fun bundleVersion(): String = manifest.mainAttributes.getValue(CPK_BUNDLE_VERSION_ATTRIBUTE)

    private fun verifyManifest() {
        with (manifest) {
            requireAttributeValueIn(CPK_FORMAT_ATTRIBUTE, "2.0")
            requireAttribute(CPK_BUNDLE_NAME_ATTRIBUTE)
            requireAttribute(CPK_BUNDLE_VERSION_ATTRIBUTE)
        }
    }

    override fun verify() {
        verifyManifest()
    }
}