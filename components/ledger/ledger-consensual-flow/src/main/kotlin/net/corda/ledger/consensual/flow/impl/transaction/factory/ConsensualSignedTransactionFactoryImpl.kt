package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionBuilderInternal
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.impl.transaction.createTransactionSignature
import net.corda.ledger.common.flow.impl.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
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
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    @Reference(service = TransactionMetadataFactory::class)
    private val transactionMetadataFactory: TransactionMetadataFactory,
    @Reference(service = WireTransactionFactory::class)
    private val wireTransactionFactory: WireTransactionFactory,
) : ConsensualSignedTransactionFactory, SingletonSerializeAsToken {

    private fun consensualMetadata() = linkedMapOf(
        TransactionMetaData.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
        TransactionMetaData.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
    )

    override fun create(
        consensualTransactionBuilder: TransactionBuilderInternal,
        signatories: Iterable<PublicKey>
    ): ConsensualSignedTransaction {
        require(signatories.toList().isNotEmpty()){
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        val metadata = transactionMetadataFactory.create(consensualMetadata())
        val wireTransaction = wireTransactionFactory.create(consensualTransactionBuilder, metadata)
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