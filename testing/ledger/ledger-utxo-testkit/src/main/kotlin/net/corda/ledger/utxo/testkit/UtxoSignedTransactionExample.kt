package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.common.testkit.signatureWithMetadataExample
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

fun UtxoSignedTransactionFactory.createExample(
    jsonMarshallingService: JsonMarshallingService,
    wireTransactionFactory: WireTransactionFactory,
    utxoSignedTransactionFactory:UtxoSignedTransactionFactory
):UtxoSignedTransaction {
    val wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService)
    return utxoSignedTransactionFactory.create(wireTransaction, listOf(signatureWithMetadataExample))
}

@Suppress("LongParameterList")
fun getUtxoSignedTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService,
    transactionSignatureService: TransactionSignatureService
): UtxoSignedTransaction {
    val wireTransaction = getWireTransactionExample(
        digestService,
        merkleTreeProvider,
        jsonMarshallingService,
        utxoTransactionMetadataExample
    )
    return UtxoSignedTransactionImpl(
        serializationService,
        transactionSignatureService,
        wireTransaction,
        listOf(signatureWithMetadataExample)
    )
}