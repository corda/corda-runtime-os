package net.corda.ledger.persistence.consensual.tests.datamodel

import java.time.Instant
import javax.persistence.EntityManagerFactory

class ConsensualEntityFactory(entityManagerFactory: EntityManagerFactory) {
    private val entityMap = entityManagerFactory.metamodel.entities.associate { it.name to it.bindableJavaType }

    val consensualCpk: Class<*> get() = classFor("ConsensualCpkEntity")
    val consensualTransaction: Class<*> get() = classFor("ConsensualTransactionEntity")
    val consensualTransactionComponent: Class<*> get() = classFor("ConsensualTransactionComponentEntity")
    val consensualTransactionStatus: Class<*> get() = classFor("ConsensualTransactionStatusEntity")
    val consensualTransactionSignature: Class<*> get() = classFor("ConsensualTransactionSignatureEntity")

    fun createConsensualCpkEntity(
        fileChecksum: String,
        name: String,
        signerSummaryHash: String,
        version: String,
        data: ByteArray,
        created: Instant
    ): Any {
        return consensualCpk.constructors.single { it.parameterCount == 6 }.newInstance(
            fileChecksum, name, signerSummaryHash, version, data, created
        )
    }

    fun createConsensualTransactionEntity(
        transactionId: String,
        privacySalt: ByteArray,
        accountId: String,
        created: Instant
    ): Any {
        return consensualTransaction.constructors.single { it.parameterCount == 4 }.newInstance(
            transactionId, privacySalt, accountId, created
        )
    }

    fun createConsensualTransactionComponentEntity(
        consensualTransaction: Any,
        groupIdx: Int,
        leafIdx: Int,
        component: ByteArray,
        hash: String,
        created: Instant
    ): Any {
        return consensualTransactionComponent.constructors.single { it.parameterCount == 6 }.newInstance(
            consensualTransaction, groupIdx, leafIdx, component, hash, created
        )
    }

    fun createConsensualTransactionStatusEntity(
        consensualTransaction: Any,
        status: String,
        created: Instant
    ): Any {
        return consensualTransactionStatus.constructors.single { it.parameterCount == 3 }.newInstance(
            consensualTransaction, status, created
        )
    }

    fun createConsensualTransactionSignatureEntity(
        consensualTransaction: Any,
        signatureIndex: Int,
        signature: ByteArray,
        publicKeyHash: String,
        created: Instant
    ): Any {
        return consensualTransactionSignature.constructors.single { it.parameterCount == 5 }.newInstance(
            consensualTransaction, signatureIndex, signature, publicKeyHash, created
        )
    }

    private fun classFor(entityName: String): Class<*> {
        return entityMap[entityName]
            ?: throw IllegalArgumentException("Unknown entity: '$entityName'")
    }
}

fun <T> Any.field(fieldName: String, type: Class<T>): T {
    val field = this::class.java.getDeclaredField(fieldName).also { f ->
        f.isAccessible = true
    }
    return type.cast(field.get(this))
}

inline fun <reified T> Any.field(fieldName: String): T = field(fieldName, T::class.java)
