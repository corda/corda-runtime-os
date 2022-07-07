package net.corda.libs.packaging.verify.internal.cpi

import net.corda.libs.packaging.verify.JarReader
import net.corda.libs.packaging.verify.internal.cpb.CpbV1Verifier

/**
 * Verifies CPI format 1.0
 */
internal class CpiV1Verifier(jarReader: JarReader): CpiVerifier {
    // CPB and CPI format 1 are same
    private val verifier = CpbV1Verifier("CPI", jarReader)

    override fun verify() {
        verifier.verify()
    }
}