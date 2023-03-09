package net.corda.uniqueness.backingstore.impl

import net.corda.uniqueness.datamodel.common.UniquenessConstants.TRANSACTION_ID_ALGO_LENGTH
import net.corda.uniqueness.datamodel.common.UniquenessConstants.TRANSACTION_ID_LENGTH
import net.corda.uniqueness.datamodel.common.UniquenessConstants.REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.NamedQueries
import javax.persistence.NamedQuery
import javax.persistence.Table

/*
 * JPA entity definitions used by the JPA backing store implementation
 */
internal object JPABackingStoreEntities {
    val classes = setOf(
        UniquenessStateDetailEntity::class.java,
        UniquenessTransactionDetailEntity::class.java,
        UniquenessRejectedTransactionEntity::class.java
    )
}

internal class UniquenessTxAlgoStateRefKey(
    val issueTxIdAlgo: String = "",
    val issueTxId: ByteArray = ByteArray(0),
    val issueTxOutputIndex: Int = 0
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UniquenessTxAlgoStateRefKey

        if (issueTxIdAlgo != other.issueTxIdAlgo) return false
        if (!issueTxId.contentEquals(other.issueTxId)) return false
        if (issueTxOutputIndex != other.issueTxOutputIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = issueTxIdAlgo.hashCode()
        result = 31 * result + issueTxId.contentHashCode()
        result = 31 * result + issueTxOutputIndex.hashCode()
        return result
    }

    companion object {
        private const val serialVersionUID = -14548L
    }
}

internal class UniquenessTxAlgoIdKey(
    val txIdAlgo: String = "",
    val txId: ByteArray = ByteArray(0),
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquenessTxAlgoIdKey) return false

        if (txIdAlgo != other.txIdAlgo) return false
        if (!txId.contentEquals(other.txId)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = txIdAlgo.hashCode()
        result = 31 * result + txId.contentHashCode()
        return result
    }

    companion object {
        private const val serialVersionUID = -30950023065170341L
    }
}

@Entity
@Table(name = "uniqueness_state_details")

// TODO this query needs refining because this way records are retrieved one-by-one which is extremely slow
@NamedQueries(
    NamedQuery(
        name = "UniquenessStateDetailEntity.select",
        query = "SELECT c FROM UniquenessStateDetailEntity c " +
            "WHERE c.issueTxIdAlgo = :txAlgo AND c.issueTxId = :txId AND c.issueTxOutputIndex = :stateIndex"
    ),
    NamedQuery(
        name = "UniquenessStateDetailEntity.consumeWithProtection",
        query = "UPDATE UniquenessStateDetailEntity SET " +
            "consumingTxIdAlgo = :consumingTxAlgo, consumingTxId = :consumingTxId " +
            "WHERE issueTxIdAlgo = :issueTxAlgo AND issueTxId = :issueTxId AND issueTxOutputIndex = :stateIndex " +
            "AND consumingTxId IS NULL" // In-flight double spend protection
    )
)

@IdClass(UniquenessTxAlgoStateRefKey::class)
internal class UniquenessStateDetailEntity(
    @Id
    @Column(name = "issue_tx_id_algo", length = TRANSACTION_ID_ALGO_LENGTH, nullable = false)
    val issueTxIdAlgo: String,

    @Id
    @Column(name = "issue_tx_id", length = TRANSACTION_ID_LENGTH, nullable = false)
    val issueTxId: ByteArray,

    @Id
    @Column(name = "issue_tx_output_idx", nullable = false)
    val issueTxOutputIndex: Int,

    @Column(name = "consuming_tx_id_algo", nullable = true, length = TRANSACTION_ID_ALGO_LENGTH)
    val consumingTxIdAlgo: String?,

    @Column(name = "consuming_tx_id", nullable = true, length = TRANSACTION_ID_LENGTH)
    val consumingTxId: ByteArray?
) {
    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquenessStateDetailEntity) return false

        if (issueTxIdAlgo != other.issueTxIdAlgo) return false
        if (!issueTxId.contentEquals(other.issueTxId)) return false
        if (issueTxOutputIndex != other.issueTxOutputIndex) return false
        if (consumingTxIdAlgo != other.consumingTxIdAlgo) return false
        if (consumingTxId != null) {
            if (other.consumingTxId == null) return false
            if (!consumingTxId.contentEquals(other.consumingTxId)) return false
        } else if (other.consumingTxId != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = issueTxIdAlgo.hashCode()
        result = 31 * result + issueTxId.contentHashCode()
        result = 31 * result + issueTxOutputIndex.hashCode()
        result = 31 * result + (consumingTxIdAlgo?.hashCode() ?: 0)
        result = 31 * result + (consumingTxId?.contentHashCode() ?: 0)
        return result
    }
}

@Entity
@Table(name = "uniqueness_tx_details")

// TODO this query needs refining because this way records are retrieved one-by-one which is extremely slow
@NamedQuery(
    name = "UniquenessTransactionDetailEntity.select",
    query = "SELECT t FROM UniquenessTransactionDetailEntity t WHERE t.txIdAlgo = :txAlgo AND t.txId = :txId"
)

@IdClass(UniquenessTxAlgoIdKey::class)
internal class UniquenessTransactionDetailEntity(
    @Id
    @Column(name = "tx_id_algo", length = TRANSACTION_ID_ALGO_LENGTH, nullable = false)
    val txIdAlgo: String,

    @Id
    @Column(name = "tx_id", length = TRANSACTION_ID_LENGTH, nullable = false)
    val txId: ByteArray,

    @Column(name = "expiry_datetime", nullable = false)
    val expiryDateTime: Instant,

    @Column(name = "commit_timestamp", nullable = false)
    val commitTimestamp: Instant,

    @Column(name = "result", nullable = false)
    val result: Char
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquenessTransactionDetailEntity) return false

        if (txIdAlgo != other.txIdAlgo) return false
        if (!txId.contentEquals(other.txId)) return false
        if (expiryDateTime != other.expiryDateTime) return false
        if (result != other.result) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = txIdAlgo.hashCode()
        result1 = 31 * result1 + txId.contentHashCode()
        result1 = 31 * result1 + expiryDateTime.hashCode()
        result1 = 31 * result1 + result.hashCode()
        return result1
    }
}

@Entity
@Table(name = "uniqueness_rejected_txs")
@NamedQuery(
    name = "UniquenessRejectedTransactionEntity.select",
    query = "SELECT t FROM UniquenessRejectedTransactionEntity t WHERE t.txIdAlgo = :txAlgo AND t.txId = :txId"
)
@IdClass(UniquenessTxAlgoIdKey::class)
internal class UniquenessRejectedTransactionEntity(
    @Id
    @Column(name = "tx_id_algo", length = TRANSACTION_ID_ALGO_LENGTH, nullable = false)
    val txIdAlgo: String,

    @Id
    // NOTE: In case of a ByteArray length is probably ignored but we keep it here just in case
    @Column(name = "tx_id", length = TRANSACTION_ID_LENGTH, nullable = false)
    val txId: ByteArray,

    @Column(name = "error_details", nullable = false) val errorDetails: ByteArray
) {
    init {
        if (errorDetails.size > REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH) {
            throw IllegalArgumentException("The maximum size of the error_details field is $REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH")
        }
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquenessRejectedTransactionEntity) return false

        if (txIdAlgo != other.txIdAlgo) return false
        if (!txId.contentEquals(other.txId)) return false
        if (!errorDetails.contentEquals(other.errorDetails)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = txIdAlgo.hashCode()
        result = 31 * result + txId.contentHashCode()
        result = 31 * result + errorDetails.contentHashCode()
        return result
    }
}
