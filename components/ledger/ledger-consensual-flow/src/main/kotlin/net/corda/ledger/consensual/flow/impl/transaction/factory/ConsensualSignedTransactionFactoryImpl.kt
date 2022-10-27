package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.impl.transaction.createTransactionSignature
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Component(
    service = [ConsensualSignedTransactionFactory::class, SingletonSerializeAsToken::class],
    scope = ServiceScope.PROTOTYPE
)
class ConsensualSignedTransactionFactoryImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = DigitalSignatureVerificationService::class)
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService
) : ConsensualSignedTransactionFactory, SingletonSerializeAsToken {

    override fun initialCreate(
        wireTransaction: WireTransaction,
        signatories: Iterable<PublicKey>
    ): ConsensualSignedTransaction {
        require(signatories.toList().isNotEmpty()){
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        val signaturesWithMetaData = signatories.map {
            createTransactionSignature(
                signingService,
                serializationService,
                getCpiSummary(),
                wireTransaction.id,
                it
            )
        }
        return ConsensualSignedTransactionImpl(
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            wireTransaction,
            signaturesWithMetaData
        )
    }


    override fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): ConsensualSignedTransaction {
        return ConsensualSignedTransactionImpl(
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            wireTransaction,
            signaturesWithMetaData
        )
    }
}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummary(
        name = "CPI name",
        version = "CPI version",
        signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )