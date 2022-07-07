package net.corda.crypto.core.aes.ecdh.impl

import net.corda.crypto.core.aes.ecdh.ECDHAgreementParams
import net.corda.crypto.core.aes.ecdh.ECDHFactory
import net.corda.crypto.core.aes.ecdh.impl.protocol.InitiatorImpl
import net.corda.crypto.core.aes.ecdh.impl.protocol.ReplierImpl
import net.corda.crypto.core.aes.ecdh.protocol.Initiator
import net.corda.crypto.core.aes.ecdh.protocol.Replier
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.jcajce.provider.util.DigestFactory
import java.security.PublicKey

//@Component
class ECDHFactoryImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val verifier: SignatureVerificationService
) : ECDHFactory {

    override fun createInitiator(
        otherStablePublicKey: PublicKey,
        ephemeralScheme: KeyScheme,
        signatureSpec: SignatureSpec
    ): Initiator =
        InitiatorImpl(schemeMetadata, verifier, otherStablePublicKey, signatureSpec, ephemeralScheme)

    override fun createReplier(
        stablePublicKey: PublicKey,
        ephemeralScheme: KeyScheme,
        signatureSpec: SignatureSpec
    ): Replier =
        ReplierImpl(schemeMetadata, stablePublicKey, signatureSpec, ephemeralScheme)

    override fun createAgreementParams(digestName: String, length: Int): ECDHAgreementParams =
        ECDHAgreementParams(
            salt = ByteArray(DigestFactory.getDigest(digestName).digestSize).apply {
                schemeMetadata.secureRandom.nextBytes(this)
            },
            digestName = digestName,
            length = length
        )
}