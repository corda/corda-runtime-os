package net.corda.ledger.utxo.testkit

import net.corda.common.json.validation.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.defaultComponentGroups
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

fun UtxoSignedTransactionFactory.createExample(
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    wireTransactionFactory: WireTransactionFactory,
    componentGroups: List<List<ByteArray>> = defaultComponentGroups +
            List(UtxoComponentGroup.values().size - defaultComponentGroups.size) { emptyList() }
):UtxoSignedTransaction {
    val wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService, jsonValidator, componentGroups)
    return create(wireTransaction, listOf(getSignatureWithMetadataExample()))
}

@Suppress("LongParameterList")
fun getUtxoSignedTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    transactionSignatureService: TransactionSignatureService,
    utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    cpkPackageSeed: String? = null
): UtxoSignedTransaction {
    val wireTransaction = getWireTransactionExample(
        digestService,
        merkleTreeProvider,
        jsonMarshallingService,
        jsonValidator,
        metadata = utxoTransactionMetadataExample(cpkPackageSeed)
    )
    return UtxoSignedTransactionImpl(
        serializationService,
        transactionSignatureService,
        utxoLedgerTransactionFactory,
        wireTransaction,
        listOf(getSignatureWithMetadataExample())
    )
}