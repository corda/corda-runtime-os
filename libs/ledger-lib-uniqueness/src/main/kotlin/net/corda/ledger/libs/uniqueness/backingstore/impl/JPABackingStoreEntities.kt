package net.corda.ledger.libs.uniqueness.backingstore.impl

import net.corda.uniqueness.datamodel.common.UniquenessConstants.ORIGINATOR_X500_NAME_LENGTH
import net.corda.uniqueness.datamodel.common.UniquenessConstants.REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH
import net.corda.uniqueness.datamodel.common.UniquenessConstants.TRANSACTION_ID_ALGO_LENGTH
import net.corda.uniqueness.datamodel.common.UniquenessConstants.TRANSACTION_ID_LENGTH
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
object JPABackingStoreEntities {
    val classes = setOf(
        UniquenessStateDetailEntity::class.java,
        UniquenessTransactionDetailEntity::class.java,
        UniquenessRejectedTransactionEntity::class.java
    )
}

class UniquenessTxAlgoStateRefKey(
    var issueTxIdAlgo: String = "",
    var issueTxId: ByteArray = ByteArray(0),
    var issueTxOutputIndex: Int = 0
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

class UniquenessTxAlgoIdKey(
    var txIdAlgo: String = "",
    var txId: ByteArray = ByteArray(0),
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
class UniquenessStateDetailEntity(
    @Id
    @Column(name = "issue_tx_id_algo", length = TRANSACTION_ID_ALGO_LENGTH, nullable = false)
    var issueTxIdAlgo: String,

    @Id
    @Column(name = "issue_tx_id", length = TRANSACTION_ID_LENGTH, nullable = false)
    var issueTxId: ByteArray,

    @Id
    @Column(name = "issue_tx_output_idx", nullable = false)
    var issueTxOutputIndex: Int,

    @Column(name = "consuming_tx_id_algo", nullable = true, length = TRANSACTION_ID_ALGO_LENGTH)
    var consumingTxIdAlgo: String?,

    @Column(name = "consuming_tx_id", nullable = true, length = TRANSACTION_ID_LENGTH)
    var consumingTxId: ByteArray?
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
@Suppress("LongParameterList")
class UniquenessTransactionDetailEntity(
    @Id
    @Column(name = "tx_id_algo", length = TRANSACTION_ID_ALGO_LENGTH, nullable = false)
    var txIdAlgo: String,

    @Id
    @Column(name = "tx_id", length = TRANSACTION_ID_LENGTH, nullable = false)
    var txId: ByteArray,

    @Column(name = "originator_x500_name", length = ORIGINATOR_X500_NAME_LENGTH, nullable = false)
    var originatorX500Name: String,

    @Column(name = "expiry_datetime", nullable = false)
    var expiryDateTime: Instant,

    @Column(name = "commit_timestamp", nullable = false)
    var commitTimestamp: Instant,

    @Column(name = "result", nullable = false)
    var result: Char
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquenessTransactionDetailEntity) return false

        if (txIdAlgo != other.txIdAlgo) return false
        if (!txId.contentEquals(other.txId)) return false
        if (originatorX500Name != other.originatorX500Name) return false
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
class UniquenessRejectedTransactionEntity(
    @Id
    @Column(name = "tx_id_algo", length = TRANSACTION_ID_ALGO_LENGTH, nullable = false)
    var txIdAlgo: String,

    @Id
    // NOTE: In case of a ByteArray length is probably ignored but we keep it here just in case
    @Column(name = "tx_id", length = TRANSACTION_ID_LENGTH, nullable = false)
    var txId: ByteArray,

    @Column(name = "error_details", nullable = false)
    var errorDetails: ByteArray
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
