package net.corda.flow.application.crypto

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
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

    private companion object {
        private val log = contextLogger()
    }

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        return externalEventExecutor.execute(
            CreateSignatureExternalEventFactory::class.java,
            SignParameters(bytes, keyEncodingService.encodeAsByteArray(publicKey), signatureSpec)
        )
    }

    @Suspendable
    override fun findMySigningKeys(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        val compositeKeys: Set<CompositeKey> = keys.filterIsInstanceTo(linkedSetOf(), CompositeKey::class.java)
        val plainKeys = keys - compositeKeys
        val compositeKeysLeaves: Set<PublicKey> = compositeKeys.flatMapTo(linkedSetOf()) {
            it.leafKeys
        }

        val foundSigningKeys = externalEventExecutor.execute(
            FilterMyKeysExternalEventFactory::class.java,
            plainKeys + compositeKeysLeaves
        ).toSet()

        val plainKeysReqResp = plainKeys.associateWith {
            if (it in foundSigningKeys) {
                it
            } else
                null
        }

        // TODO For now we are going to be matching composite key request with first leaf found ignoring other found leaves
        //  Perhaps we should revisit this behavior in the future.
        val compositeKeysReqResp = compositeKeys.associateWith {
            var foundLeaf: PublicKey? = null
            it.leafKeys.forEach { leaf ->
                if (leaf in foundSigningKeys) {
                    if (foundLeaf == null) {
                        foundLeaf = leaf
                    } else {
                        log.info(
                            "Found multiple composite key leaves to be owned for the same composite key by the same node " +
                                    "while there should only be one per composite key per node. " +
                                    "Composite key: \"$it\" " +
                                    "Will make use of firstly found leaf: \"$foundLeaf\" " +
                                    "Will ignore also found leaf: \"$leaf\""
                        )
                    }
                }
            }
            foundLeaf
        }

        return plainKeysReqResp + compositeKeysReqResp
    }
}
