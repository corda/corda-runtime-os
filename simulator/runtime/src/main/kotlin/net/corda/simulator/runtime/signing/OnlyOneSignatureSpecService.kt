package net.corda.simulator.runtime.signing

import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

class OnlyOneSignatureSpecService : SignatureSpecService {
    override fun compatibleSignatureSpecs(publicKey: PublicKey): List<SignatureSpec> {
        return listOf(SignatureSpec.ECDSA_SHA256)
    }

    override fun compatibleSignatureSpecs(
        publicKey: PublicKey,
        digestAlgorithmName: DigestAlgorithmName
    ): List<SignatureSpec> {
        return listOf(SignatureSpec.ECDSA_SHA256)
    }

    override fun defaultSignatureSpec(publicKey: PublicKey): SignatureSpec? {
        return SignatureSpec.ECDSA_SHA256
    }

    override fun defaultSignatureSpec(publicKey: PublicKey, digestAlgorithmName: DigestAlgorithmName): SignatureSpec? {
        return SignatureSpec.ECDSA_SHA256
    }

}
