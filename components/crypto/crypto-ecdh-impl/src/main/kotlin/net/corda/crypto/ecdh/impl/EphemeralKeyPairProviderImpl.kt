package net.corda.crypto.ecdh.impl

import net.corda.crypto.ecdh.ECDHKeyPair
import net.corda.crypto.ecdh.EphemeralKeyPairProvider
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.KeyPairGenerator
import java.security.PublicKey

@Component(service = [EphemeralKeyPairProvider::class])
class EphemeralKeyPairProviderImpl(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : EphemeralKeyPairProvider {
    override fun create(
        otherStablePublicKey: PublicKey,
        digestName: String
    ): ECDHKeyPair {
        val scheme = schemeMetadata.findKeyScheme(otherStablePublicKey)
        val provider = schemeMetadata.providers.getValue(scheme.providerName)
        val keyPairGenerator = KeyPairGenerator.getInstance(
            scheme.algorithmName,
            provider
        )
        if (scheme.algSpec != null) {
            keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
        } else if (scheme.keySize != null) {
            keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
        }
        return EphemeralKeyPair(
            provider,
            keyPairGenerator.generateKeyPair(),
            otherStablePublicKey,
            digestName
        )
    }
}