package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.verify.internal.Verifier

interface CpkVerifier: Verifier {
    val id: CpkIdentifier
    val dependencies: CpkDependencies
}