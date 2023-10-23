package net.corda.ledger.persistence.utxo.tests.datamodel

import java.time.Instant
import javax.persistence.EntityManagerFactory

class UtxoEntityFactory(entityManagerFactory: EntityManagerFactory) {
    private val entityMap = entityManagerFactory.metamodel.entities.associate { it.name to it.bindableJavaType }

    val utxoTransaction: Class<*> get() = classFor("UtxoTransactionEntity")
    val utxoTransactionComponent: Class<*> get() = classFor("UtxoTransactionComponentEntity")
    val utxoVisibleTransactionOutput: Class<*> get() = classFor("UtxoVisibleTransactionOutputEntity")
    val utxoTransactionSignature: Class<*> get() = classFor("UtxoTransactionSignatureEntity")

    fun createUtxoTransactionEntity(
        transactionId: String,
        privacySalt: ByteArray,
        accountId: String,
        created: Instant,
        status: String,
        updated: Instant
    ): Any {
        return utxoTransaction.constructors.single { it.parameterCount == 6 }.newInstance(
            transactionId, privacySalt, accountId, created, status, updated
        )
    }

    fun createUtxoTransactionComponentEntity(
        utxoTransaction: Any,
        groupIdx: Int,
        leafIdx: Int,
        component: ByteArray,
        hash: String
    ): Any {
        return utxoTransactionComponent.constructors.single { it.parameterCount == 5 }.newInstance(
            utxoTransaction, groupIdx, leafIdx, component, hash
        )
    }

    fun createUtxoTransactionSignatureEntity(
        utxoTransaction: Any,
        signatureIndex: Int,
        signature: ByteArray,
        publicKeyHash: String,
        created: Instant
    ): Any {
        return utxoTransactionSignature.constructors.single { it.parameterCount == 5 }.newInstance(
            utxoTransaction, signatureIndex, signature, publicKeyHash, created
        )
    }

    private fun classFor(entityName: String): Class<*> {
        return entityMap[entityName]
            ?: throw IllegalArgumentException("Unknown entity: '$entityName'")
    }
}
