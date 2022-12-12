package net.corda.flow.application.crypto

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandbox.type.UsedByFlow
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
}
