package net.corda.ledger.persistence.utxo.tests.datamodel

import net.corda.orm.utils.transaction
import java.time.Instant
import javax.persistence.EntityManagerFactory

class UtxoEntityFactory(private val entityManagerFactory: EntityManagerFactory) {
    private val entityMap = entityManagerFactory.metamodel.entities.associate { it.name to it.bindableJavaType }

    val utxoTransaction: Class<*> get() = classFor("UtxoTransactionEntity")
    val utxoTransactionMetadata: Class<*> get() = classFor("UtxoTransactionMetadataEntity")
    val utxoTransactionComponent: Class<*> get() = classFor("UtxoTransactionComponentEntity")
    val utxoVisibleTransactionOutput: Class<*> get() = classFor("UtxoVisibleTransactionOutputEntity")
    val utxoTransactionSignature: Class<*> get() = classFor("UtxoTransactionSignatureEntity")
    val utxoTransactionSource: Class<*> get() = classFor("UtxoTransactionSourceEntity")
    val merkleProof: Class<*> get() = classFor("UtxoMerkleProofEntity")

    @Suppress("LongParameterList")
    fun createUtxoTransactionEntity(
        transactionId: String,
        privacySalt: ByteArray,
        accountId: String,
        created: Instant,
        status: String,
        updated: Instant,
        utxoTransactionMetadata: Any,
        isFiltered: Boolean = false
    ): Any {
        return utxoTransaction.constructors.single { it.parameterCount == 8 }.newInstance(
            transactionId,
            privacySalt,
            accountId,
            created,
            status,
            updated,
            utxoTransactionMetadata,
            isFiltered
        )
    }

    fun createOrFindUtxoTransactionMetadataEntity(
        hash: String,
        canonicalData: ByteArray,
        groupParametersHash: String,
        cpiFileChecksum: String,
    ): Any {
        return entityManagerFactory.transaction { em ->
            em.find(utxoTransactionMetadata, hash) ?: createUtxoTransactionMetadataEntity(
                hash,
                canonicalData,
                groupParametersHash,
                cpiFileChecksum,
            ).also {
                em.persist(it)
            }
        }
    }

    fun createUtxoTransactionMetadataEntity(
        hash: String,
        canonicalData: ByteArray,
        groupParametersHash: String,
        cpiFileChecksum: String,
    ): Any {
        return utxoTransactionMetadata.constructors.single { it.parameterCount == 4 }.newInstance(
            hash,
            canonicalData,
            groupParametersHash,
            cpiFileChecksum
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
            utxoTransaction,
            groupIdx,
            leafIdx,
            component,
            hash
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
            utxoTransaction,
            signatureIndex,
            signature,
            publicKeyHash,
            created
        )
    }

    private fun classFor(entityName: String): Class<*> {
        return entityMap[entityName]
            ?: throw IllegalArgumentException("Unknown entity: '$entityName'")
    }
}
