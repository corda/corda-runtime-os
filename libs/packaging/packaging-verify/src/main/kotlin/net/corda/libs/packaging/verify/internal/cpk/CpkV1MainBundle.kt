package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.verify.JarReader
import net.corda.libs.packaging.PackagingConstants.CPK_BUNDLE_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_BUNDLE_VERSION_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.WORKFLOW_LICENCE_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.WORKFLOW_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.WORKFLOW_VENDOR_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.WORKFLOW_VERSION_ATTRIBUTE
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.verify.internal.firstOrThrow
import net.corda.libs.packaging.verify.internal.requireAttribute

/**
 * Verifies Main Bundle from CPK format 1.0
 */
internal class CpkV1MainBundle(jarReader: JarReader) {
    private val name = jarReader.jarName
    internal val manifest = jarReader.manifest
    private val codeSigners = jarReader.codeSigners
    internal val cpkDependencies: CpkDependencies
    internal val libraryConstraints: CpkLibraryConstraints

    init {
        cpkDependencies = jarReader.entries.filter{ it.name == CPK_DEPENDENCIES_FILE_ENTRY }
            .map { CpkDependencies(it.name, it.createInputStream(), codeSigners.toTypedArray()) }
            .firstOrThrow(PackagingException("$CPK_DEPENDENCIES_FILE_ENTRY not found in CPK main bundle \"$name\""))

        libraryConstraints = jarReader.entries.filter{ it.name == CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY }
            .map { CpkLibraryConstraints(it.name, it.createInputStream()) }
            .firstOrThrow(PackagingException("$CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY not found in CPK main bundle \"$name\""))
    }

    private fun verifyManifest() {
        with (manifest) {
            requireAttribute(CPK_BUNDLE_NAME_ATTRIBUTE)
            requireAttribute(CPK_BUNDLE_VERSION_ATTRIBUTE)
            requireAttribute(WORKFLOW_LICENCE_ATTRIBUTE)
            requireAttribute(WORKFLOW_NAME_ATTRIBUTE)
            requireAttribute(WORKFLOW_VENDOR_ATTRIBUTE)
            requireAttribute(WORKFLOW_VERSION_ATTRIBUTE)
        }
    }

    fun verify() {
        verifyManifest()
    }
}