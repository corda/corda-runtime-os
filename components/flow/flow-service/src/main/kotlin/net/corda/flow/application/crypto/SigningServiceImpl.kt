package net.corda.flow.application.crypto

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PublicKey

@Component(
    service = [SigningService::class, UsedByFlow::class],
    scope = PROTOTYPE
)
class SigningServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : SigningService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        return externalEventExecutor.execute(
            CreateSignatureExternalEventFactory::class.java,
            SignParameters(bytes, keyEncodingService.encodeAsByteArray(publicKey), signatureSpec)
        )
    }

    @Suspendable
    override fun getMySigningKeys(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        val compositeKeys: Set<CompositeKey> = keys.filterIsInstanceTo(linkedSetOf(), CompositeKey::class.java)
        val plainKeys = keys - compositeKeys

        val compositeKeysLeaves: Set<PublicKey> = compositeKeys.flatMapTo(linkedSetOf()) {
            it.leafKeys
        }

        val keysToLookFor = plainKeys + compositeKeysLeaves
        val foundSigningKeys = externalEventExecutor.execute(
            FilterMyKeysExternalEventFactory::class.java,
            keysToLookFor
        ).toSet()

        require(foundSigningKeys.size <= keysToLookFor.size) {
            "Found keys cannot be more than requested keys"
        }

        val plainKeysReqResp = plainKeys.associate {
            if (it in foundSigningKeys) {
                it to it
            } else
                it to null
        }

        val compositeKeysReqResp = compositeKeys.associateWith {
            var foundLeaf: PublicKey? = null
            it.leafKeys.forEach { leaf ->
                if (leaf in foundSigningKeys) {
                    if (foundLeaf == null) {
                        foundLeaf = leaf
                    } else {
                        throw IllegalStateException(
                            "A node should be owning one key at most per composite key, but two owned keys were found for composite key: \"$it\" " +
                                    " first: \"$foundLeaf\" second: \"$leaf\""
                        )
                    }
                }
            }
            foundLeaf
        }

        return plainKeysReqResp + compositeKeysReqResp
    }
}
