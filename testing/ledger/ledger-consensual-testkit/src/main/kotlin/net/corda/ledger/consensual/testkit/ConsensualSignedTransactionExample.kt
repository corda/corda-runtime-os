package net.corda.ledger.consensual.testkit

import net.corda.common.json.validation.JsonValidator
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.common.testkit.signatureWithMetadataExample
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

fun ConsensualSignedTransactionFactory.createExample(
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    wireTransactionFactory: WireTransactionFactory
): ConsensualSignedTransaction {
    val wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService, jsonValidator)
    return create(wireTransaction, listOf(signatureWithMetadataExample))
}

@Suppress("LongParameterList")
fun getConsensualSignedTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    transactionSignatureService: TransactionSignatureService
): ConsensualSignedTransaction {
    val wireTransaction = getWireTransactionExample(
        digestService,
        merkleTreeProvider,
        jsonMarshallingService,
        jsonValidator,
        metadata = consensualTransactionMetadataExample
    )
    return ConsensualSignedTransactionImpl(
        serializationService,
        transactionSignatureService,
        wireTransaction,
        listOf(signatureWithMetadataExample)
    )
}
