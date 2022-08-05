package net.corda.flow.application.crypto

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PublicKey

@Component(service = [DigitalSignatureVerificationService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class DigitalSignatureVerificationServiceImpl @Activate constructor(
    @Reference(service = SignatureVerificationService::class)
    private val signatureVerificationService: SignatureVerificationService
) : DigitalSignatureVerificationService, SingletonSerializeAsToken {

    @Suspendable
    override fun verify(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray) {
        return signatureVerificationService.verify(publicKey, signatureSpec, signatureData, clearData)
    }
}