package net.corda.libs.packaging.verify.internal.cpi

import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.verify.JarReader
import net.corda.libs.packaging.verify.internal.cpb.CpbV1Verifier
import net.corda.libs.packaging.verify.internal.firstOrThrow

/**
 * Verifies CPI format 1.0
 */
class CpiV1Verifier(jarReader: JarReader): CpiVerifier {
    private val name = jarReader.jarName
    private val verifier = CpbV1Verifier("CPI", jarReader)
    private val groupPolicy: GroupPolicy

    init {
        groupPolicy = jarReader.entries.filter(::isGroupPolicy).map { GroupPolicy() }
            .firstOrThrow(PackagingException("Group policy not found in CPI \"$name\""))
    }

    private fun isGroupPolicy(entry: JarReader.Entry): Boolean =
        entry.name.equals("META-INF/${PackagingConstants.CPI_GROUP_POLICY_ENTRY}", ignoreCase = true)

    override fun verify() {
        verifier.verify()
        groupPolicy.verify()
    }
}