package net.corda.ledger.lib.impl.stub.external.event

import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullId
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.lib.common.Constants.TENANT_ID
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.cipherSchemeMetadata
import java.security.PublicKey

class SigningServiceExternalEventExecutor(private val cryptoService: CryptoService) : ExternalEventExecutor {

    @Suppress("UNCHECKED_CAST")
    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        // FIXME HACK!!!! we either get a set param then we need to find the keys or sign params then we need to sign
        return when (parameters) {
            is Set<*> -> {
                val keySet = parameters as Set<PublicKey>
                cryptoService.lookupSigningKeysByPublicKeyShortHash(
                    TENANT_ID,
                    keySet.map { ShortHash.Companion.of(it.fullId()) }
                ).map { it.publicKey } as RESUME
            }
            is SignParameters -> {
                val decodedPublicKey = cipherSchemeMetadata.decodePublicKey(parameters.encodedPublicKeyBytes)
                cryptoService.sign(
                    TENANT_ID,
                    decodedPublicKey,
                    parameters.signatureSpec,
                    parameters.bytes,
                    emptyMap()
                ) as RESUME
            }
            else -> throw IllegalArgumentException("currently not supported")
        }
    }
}
