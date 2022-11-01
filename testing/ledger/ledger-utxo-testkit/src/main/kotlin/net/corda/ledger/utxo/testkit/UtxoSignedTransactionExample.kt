package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.common.testkit.signatureWithMetaDataExample
import net.corda.ledger.utxo.impl.transaction.UtxoSignedTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@Suppress("LongParameterList")
fun getUtxoSignedTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService,
    signingService: SigningService,
    digitalSignatureVerificationService: DigitalSignatureVerificationService
): UtxoSignedTransaction {
    val wireTransaction = getWireTransactionExample(
        digestService,
        merkleTreeProvider,
        jsonMarshallingService,
        utxoTransactionMetaDataExample
    )
    return UtxoSignedTransactionImpl(
        serializationService,
        signingService,
        digitalSignatureVerificationService,
        wireTransaction,
        listOf(signatureWithMetaDataExample)
    )
}