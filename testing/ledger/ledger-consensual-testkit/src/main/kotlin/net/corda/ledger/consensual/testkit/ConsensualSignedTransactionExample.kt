package net.corda.ledger.consensual.testkit

import net.corda.ledger.common.testkit.getWireTransaction
import net.corda.ledger.common.testkit.signatureWithMetaDataExample
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

@Suppress("Unused", "LongParameterList")
fun getConsensualSignedTransaction(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService,
    signingService: SigningService,
    digitalSignatureVerificationService: DigitalSignatureVerificationService
): ConsensualSignedTransaction {
    val wireTransaction = getWireTransaction(
        digestService,
        merkleTreeProvider,
        jsonMarshallingService,
        consensualTransactionMetaDataExample
    )
    return ConsensualSignedTransactionImpl(
        serializationService,
        signingService,
        digitalSignatureVerificationService,
        wireTransaction,
        listOf(signatureWithMetaDataExample)
    )
}