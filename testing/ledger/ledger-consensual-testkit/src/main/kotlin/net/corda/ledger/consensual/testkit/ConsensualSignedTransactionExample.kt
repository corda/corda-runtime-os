package net.corda.ledger.consensual.testkit

import net.corda.ledger.common.testkit.getWireTransaction
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import java.security.KeyPairGenerator
import java.time.Instant

fun getConsensualSignedTransactionImpl(
    digestService: DigestService,
    merkleTreeFactory: MerkleTreeFactory,
    serializationService: SerializationService,
    jsonMarshallingService: JsonMarshallingService
): ConsensualSignedTransactionImpl {
    val wireTransaction = getWireTransaction(digestService, merkleTreeFactory, jsonMarshallingService)

    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(512)
    val testPublicKey = kpg.genKeyPair().public

    val signature = DigitalSignature.WithKey(testPublicKey, "0".toByteArray(), mapOf())
    val digitalSignatureMetadata =
        DigitalSignatureMetadata(Instant.now(), mapOf()) //CORE-5091 populate this properly...
    val signatureWithMetaData = DigitalSignatureAndMetadata(signature, digitalSignatureMetadata)
    return ConsensualSignedTransactionImpl(serializationService, wireTransaction, listOf(signatureWithMetaData))
}

