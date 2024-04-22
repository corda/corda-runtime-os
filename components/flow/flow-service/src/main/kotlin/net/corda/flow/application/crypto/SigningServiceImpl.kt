package net.corda.flow.application.crypto

import io.micrometer.core.instrument.Timer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PublicKey

@Component(
    service = [SigningService::class, UsedByFlow::class],
    scope = PROTOTYPE
)
class SigningServiceImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = MySigningKeysCache::class)
    private val mySigningKeysCache: MySigningKeysCache
) : SigningService, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(SigningServiceImpl::class.java)
    }

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKeyId {
        return recordSuspendable({ cryptoFlowTimer("sign") }) @Suspendable {
            val digitalSignatureWithKey = externalEventExecutor.execute(
                CreateSignatureExternalEventFactory::class.java,
                SignParameters(bytes, keyEncodingService.encodeAsByteArray(publicKey), signatureSpec)
            )

            DigitalSignatureWithKeyId(
                // TODO the following static conversion to key id needs to be replaced with fetching the key id from crypto worker DB
                //  as recorded in https://r3-cev.atlassian.net/browse/CORE-12033
                digitalSignatureWithKey.by.fullIdHash(),
                digitalSignatureWithKey.bytes
            )
        }
    }

    @Suspendable
    override fun findMySigningKeys(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        val operation = operation@ @Suspendable {
            val cachedKeys = mySigningKeysCache.get(keys)

            if (cachedKeys.size == keys.size) {
                return@operation cachedKeys
            }

            val keysToFind = keys - cachedKeys.keys

            val compositeKeys: Set<CompositeKey> = keysToFind.filterIsInstanceTo(linkedSetOf(), CompositeKey::class.java)
            val plainKeys = keysToFind - compositeKeys
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

            mySigningKeysCache.putAll(plainKeysReqResp)
            mySigningKeysCache.putAll(compositeKeysReqResp)

            return@operation cachedKeys + plainKeysReqResp + compositeKeysReqResp
        }
        return recordSuspendable({ cryptoFlowTimer("findMySigningKeys") }, operation)
    }

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        return recordSuspendable({ cryptoFlowTimer("decodePublicKeyFromByteArray") }) {
            keyEncodingService.decodePublicKey(encodedKey)
        }
    }

    override fun decodePublicKey(encodedKey: String): PublicKey {
        return recordSuspendable({ cryptoFlowTimer("decodePublicKeyFromString") }) {
            keyEncodingService.decodePublicKey(encodedKey)
        }
    }

    override fun encodeAsByteArray(publicKey: PublicKey): ByteArray {
        return recordSuspendable({ cryptoFlowTimer("encodePublicKeyToByteArray") }) {
            keyEncodingService.encodeAsByteArray(publicKey)
        }
    }

    override fun encodeAsString(publicKey: PublicKey): String {
        return recordSuspendable({ cryptoFlowTimer("encodePublicKeyToString") }) {
            keyEncodingService.encodeAsString(publicKey)
        }
    }

    private fun cryptoFlowTimer(operationName: String): Timer {
        return CordaMetrics.Metric.CryptoOperationsFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.OperationName, operationName)
            .build()
    }

}
