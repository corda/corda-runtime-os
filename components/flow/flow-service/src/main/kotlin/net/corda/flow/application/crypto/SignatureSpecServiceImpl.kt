package net.corda.flow.application.crypto

import java.security.PublicKey
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [SignatureSpecService::class, SingletonSerializeAsToken::class],
    scope = PROTOTYPE
)
class SignatureSpecServiceImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : SignatureSpecService, SingletonSerializeAsToken {

    @Suspendable
    override fun defaultSignatureSpec(publicKey: PublicKey): SignatureSpec {
        TODO()
    }

    @Suspendable
    override fun defaultSignatureSpec(publicKey: PublicKey, digestAlgorithm: DigestAlgorithmName): SignatureSpec? =
        schemeMetadata.inferSignatureSpec(publicKey, digestAlgorithm)

    @Suspendable
    override fun compatibleSignatureSpecs(publicKey: PublicKey): List<SignatureSpec> {
        val keyScheme = schemeMetadata.findKeyScheme(publicKey)
        return schemeMetadata.supportedSignatureSpec(keyScheme)
    }

    @Suspendable
    override fun compatibleSignatureSpecs(publicKey: PublicKey, hash: DigestAlgorithmName): List<SignatureSpec> {
        TODO()
    }
}