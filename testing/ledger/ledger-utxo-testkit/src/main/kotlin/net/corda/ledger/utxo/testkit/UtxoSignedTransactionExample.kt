package net.corda.ledger.utxo.testkit

import net.corda.common.json.validation.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.defaultComponentGroups
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.common.testkit.signatureWithMetadataExample
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

fun UtxoSignedTransactionFactory.createExample(
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    wireTransactionFactory: WireTransactionFactory,
    componentGroups: List<List<ByteArray>> = defaultComponentGroups
):UtxoSignedTransaction {
    val wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService, jsonValidator, componentGroups)
    return create(wireTransaction, listOf(signatureWithMetadataExample))
}

@Suppress("LongParameterList")
fun getUtxoSignedTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    transactionSignatureService: TransactionSignatureService
): UtxoSignedTransaction {
    val wireTransaction = getWireTransactionExample(
        digestService,
        merkleTreeProvider,
        jsonMarshallingService,
        jsonValidator,
        metadata = utxoTransactionMetadataExample
    )
    return UtxoSignedTransactionImpl(
        serializationService,
        transactionSignatureService,
        wireTransaction,
        listOf(signatureWithMetadataExample)
    )
}