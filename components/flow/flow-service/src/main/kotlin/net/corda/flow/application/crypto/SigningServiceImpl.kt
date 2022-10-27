package net.corda.flow.application.crypto

import java.security.PublicKey
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.DigitalSignatureWithSpec
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ SigningService::class, SingletonSerializeAsToken::class ],
    scope = PROTOTYPE
)
class SigningServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : SigningService, SingletonSerializeAsToken {

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        val signParameters = SignParameters(bytes, keyEncodingService.encodeAsByteArray(publicKey), signatureSpec)
        return doSign(signParameters) as DigitalSignature.WithKey
    }

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignatureWithSpec {
        val signParametersWithoutSpec = SignParameters(bytes, keyEncodingService.encodeAsByteArray(publicKey), null)
        return doSign(signParametersWithoutSpec) as DigitalSignatureWithSpec
    }

    @Suspendable
    private fun doSign(signParameters: SignParameters): Any {
        return externalEventExecutor.execute(
            CreateSignatureExternalEventFactory::class.java,
            signParameters
        )
    }
}
