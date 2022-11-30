package net.corda.flow.application.crypto

import java.security.PublicKey
import net.corda.sandbox.type.UsedByFlow
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
    service = [SignatureSpecService::class, UsedByFlow::class],
    scope = PROTOTYPE
)
class SignatureSpecServiceImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : SignatureSpecService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun defaultSignatureSpec(publicKey: PublicKey): SignatureSpec? =
        schemeMetadata.defaultSignatureSpec(publicKey)

    @Suspendable
    override fun defaultSignatureSpec(publicKey: PublicKey, digestAlgorithmName: DigestAlgorithmName): SignatureSpec? =
        schemeMetadata.inferSignatureSpec(publicKey, digestAlgorithmName)

    @Suspendable
    override fun compatibleSignatureSpecs(publicKey: PublicKey): List<SignatureSpec> {
        val keyScheme = schemeMetadata.findKeyScheme(publicKey)
        return schemeMetadata.supportedSignatureSpec(keyScheme)
    }

    @Suspendable
    override fun compatibleSignatureSpecs(publicKey: PublicKey, digestAlgorithmName: DigestAlgorithmName): List<SignatureSpec> {
        val keyScheme = schemeMetadata.findKeyScheme(publicKey)
        return schemeMetadata.supportedSignatureSpec(keyScheme, digestAlgorithmName)
    }
}