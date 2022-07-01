package net.corda.flow.application.crypto

import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PublicKey
import java.util.UUID

@Component(service = [SigningService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class SigningServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : SigningService, SingletonSerializeAsToken {

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        return flowFiberService.getExecutingFiber().suspend(
            FlowIORequest.SignBytes(
                requestId = UUID.randomUUID().toString(),
                bytes,
                publicKey,
                signatureSpec
            )
        )
    }

    @Suspendable
    override fun decodePublicKey(encodedKey: String): PublicKey {
        return keyEncodingService.decodePublicKey(encodedKey)
    }
}