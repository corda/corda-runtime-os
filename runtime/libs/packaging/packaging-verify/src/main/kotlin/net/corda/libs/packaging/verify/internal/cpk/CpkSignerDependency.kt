package net.corda.libs.packaging.verify.internal.cpk

import java.security.CodeSigner

/** CPK dependency matched by code signers */
internal data class CpkSignerDependency (
    override val name: String,
    override val version: String,
    val codeSigners: List<CodeSigner>
): CpkDependency {
    override fun satisfied(cpks: List<AvailableCpk>): Boolean =
        cpks.any {
            it.name == name &&
            it.version == version &&
            it.codeSigners.certificates() == codeSigners.certificates()
        }

    private fun List<CodeSigner>.certificates() =
        mapTo(HashSet()) { it.signerCertPath.certificates.first() }
}