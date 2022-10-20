package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.testkit.getWireTransaction
import net.corda.ledger.utxo.impl.transaction.UtxoSignedTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.KeyPairGenerator
import java.time.Instant

@Suppress("Unused", "LongParameterList")
fun getUtxoSignedTransaction(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService,
    signingService: SigningService,
    digitalSignatureVerificationService: DigitalSignatureVerificationService
): UtxoSignedTransaction {
    val wireTransaction = getWireTransaction(
        digestService,
        merkleTreeProvider,
        jsonMarshallingService,
        utxoTransactionMetaDataExample
    )

    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(512)
    val testPublicKey = kpg.genKeyPair().public

    val signature = DigitalSignature.WithKey(testPublicKey, "0".toByteArray(), mapOf())
    val digitalSignatureMetadata =
        DigitalSignatureMetadata(Instant.now(), mapOf())
    val signatureWithMetaData = DigitalSignatureAndMetadata(signature, digitalSignatureMetadata)
    return UtxoSignedTransactionImpl(
        serializationService,
        signingService,
        digitalSignatureVerificationService,
        wireTransaction,
        listOf(signatureWithMetaData)
    )
}

