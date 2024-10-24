package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.ledger.libs.common.flow.impl.transaction.TransactionSignatureVerificationServiceImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [
        TransactionSignatureVerificationService::class,
        TransactionSignatureVerificationServiceInternal::class,
        UsedByFlow::class,
        UsedByVerification::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class TransactionSignatureVerificationOsgiServiceImpl private constructor(
    delegate: TransactionSignatureVerificationServiceInternal
) : TransactionSignatureVerificationServiceInternal by delegate,
    SingletonSerializeAsToken,
    UsedByFlow,
    UsedByVerification {

    @Suppress("Unused", "LongParameterList")
    @Activate
    constructor(
        @Reference(service = SerializationServiceInternal::class)
        serializationService: SerializationServiceInternal,
        @Reference(service = DigitalSignatureVerificationService::class)
        digitalSignatureVerificationService: DigitalSignatureVerificationService,
        @Reference(service = SignatureSpecService::class)
        signatureSpecService: SignatureSpecService,
        @Reference(service = MerkleTreeProvider::class)
        merkleTreeProvider: MerkleTreeProvider,
        @Reference(service = DigestService::class)
        digestService: DigestService,
        @Reference(service = KeyEncodingService::class)
        keyEncodingService: KeyEncodingService
    ) : this(
        TransactionSignatureVerificationServiceImpl(
            serializationService,
            digitalSignatureVerificationService,
            signatureSpecService,
            merkleTreeProvider,
            digestService,
            keyEncodingService
        )
    )
}
