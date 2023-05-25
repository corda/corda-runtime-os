package net.corda.flow.application.crypto

import io.micrometer.core.instrument.Timer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.Suspendable
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
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = MySigningKeysCache::class)
    private val mySigningKeysCache: MySigningKeysCache
) : SigningService, UsedByFlow, SingletonSerializeAsToken {

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
        return recordSuspendable({ cryptoFlowTimer("findMySigningKeys") }) @Suspendable {
            mySigningKeysCache.get(keys)
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
