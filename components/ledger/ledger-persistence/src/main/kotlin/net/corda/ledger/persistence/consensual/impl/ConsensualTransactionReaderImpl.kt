package net.corda.ledger.persistence.consensual.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.persistence.common.LedgerPersistenceUtils.findAccount
import net.corda.ledger.persistence.consensual.ConsensualTransactionReader
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary

class ConsensualTransactionReaderImpl(
    serializer: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val transaction: PersistTransaction
) : ConsensualTransactionReader {

    private val signedTransaction = serializer.deserialize<SignedTransactionContainer>(transaction.transaction.array())

    override val id: SecureHash
        get() = signedTransaction.id

    override val account: String
        get() = externalEventContext.findAccount()

    override val status: TransactionStatus
        get() = transaction.status.toTransactionStatus()

    override val privacySalt: PrivacySalt
        get() = signedTransaction.wireTransaction.privacySalt

    override val rawGroupLists: List<List<ByteArray>>
        get() = signedTransaction.wireTransaction.componentGroupLists

    override val signatures: List<DigitalSignatureAndMetadata>
        get() = signedTransaction.signatures

    override val cpkMetadata: List<CordaPackageSummary>
        get() = (signedTransaction.wireTransaction.metadata as TransactionMetadataInternal).getCpkMetadata()
}
