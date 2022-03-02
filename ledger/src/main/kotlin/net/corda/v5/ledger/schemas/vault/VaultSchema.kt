package net.corda.v5.ledger.schemas.vault

import net.corda.v5.application.identity.AbstractParty
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.toStringShort
import net.corda.v5.ledger.UniqueIdentifier
import net.corda.v5.ledger.contracts.CPKConstraint
import net.corda.v5.ledger.contracts.SignatureCPKConstraint
import net.corda.v5.ledger.schemas.DirectStatePersistable
import net.corda.v5.ledger.schemas.IndirectStatePersistable
import net.corda.v5.ledger.schemas.PersistentState
import net.corda.v5.ledger.schemas.PersistentStateRef
import net.corda.v5.ledger.services.vault.RelevancyStatus
import net.corda.v5.ledger.services.vault.StateStatus
import net.corda.v5.ledger.transactions.MAX_NUMBER_OF_KEYS_IN_SIGNATURE_CONSTRAINT
import net.corda.v5.persistence.MappedSchema
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Embedded
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

/**
 * JPA representation of the core Vault Schema
 */
object VaultSchema

/**
 * First version of the Vault ORM schema
 */
@Suppress("MagicNumber") // database column length
@CordaSerializable
object VaultSchemaV1 : MappedSchema(
    schemaFamily = VaultSchema.javaClass,
    version = 1,
    mappedTypes = listOf(
        VaultState::class.java,
        VaultLinearState::class.java,
        VaultFungibleState::class.java,
        VaultTxnNote::class.java,
        PersistentParty::class.java,
        StateToExternalId::class.java,
        DbVaultStateEvent::class.java
    )
) {

    override val migrationResource = "vault-schema.changelog-master"

    /**
     *  Constraint information associated with a [net.corda.v5.ledger.contracts.ContractState].
     *  See [CPKConstraint]
     */
    @CordaSerializable
    data class ConstraintInfo(val constraint: CPKConstraint) {
        @CordaSerializable
        enum class Type {
            SIGNATURE
        }

        fun type(): Type {
            return when (constraint) {
                is SignatureCPKConstraint -> Type.SIGNATURE
                else -> throw IllegalArgumentException("Invalid constraint type: $constraint")
            }
        }

        fun data(): ByteArray? {
            return when (type()) {
                Type.SIGNATURE -> (constraint as SignatureCPKConstraint).key.bytes
            }
        }
    }

    @Suppress("LongParameterList")
    @Entity
    @Table(
        name = "vault_state",
        indexes = [
            Index(name = "state_status_idx", columnList = "state_status"), Index(
                name = "lock_id_idx",
                columnList = "lock_id, state_status"
            )
        ]
    )
    class VaultState(
        /** NOTE: serialized transaction state (including contract state) is now resolved from transaction store */

        /** refers to the X500Name of the notary a state is attached to */
        @Column(name = "notary_name", nullable = false)
        var notary: Party,

        /** references a concrete ContractState that is [net.corda.v5.ledger.schemas.QueryableState] and has a [MappedSchema] */
        @Column(name = "contract_state_class_name", nullable = false)
        var contractStateClassName: String,

        /** state lifecycle: unconsumed, consumed */
        @Column(name = "state_status", nullable = false)
        var stateStatus: StateStatus,

        /** refers to timestamp recorded upon entering UNCONSUMED state */
        @Column(name = "recorded_timestamp", nullable = false)
        var recordedTime: Instant,

        /** refers to timestamp recorded upon entering CONSUMED state */
        @Column(name = "consumed_timestamp", nullable = true)
        var consumedTime: Instant? = null,

        /** used to denote a state has been soft locked (to prevent double spend)
         *  will contain a temporary unique [UUID] obtained from a flow session */
        @Column(name = "lock_id", nullable = true)
        var lockId: String? = null,

        /** Used to determine whether a state abides by the relevancy rules of the recording node */
        @Column(name = "relevancy_status", nullable = false)
        var relevancyStatus: RelevancyStatus,

        /** refers to the last time a lock was taken (reserved) or updated (released, re-reserved) */
        @Column(name = "lock_timestamp", nullable = true)
        var lockUpdateTime: Instant? = null,

        /** refers to constraint type (none, hash, whitelisted, signature) associated with a contract state */
        @Column(name = "constraint_type", nullable = false)
        var constraintType: ConstraintInfo.Type,

        /** associated constraint type data (if any) */
        // TODO: remove org.hibernate.annotations in API
        @Suppress("DEPRECATION", "ForbiddenComment")
        @Column(name = "constraint_data", length = MAX_CONSTRAINT_DATA_SIZE, nullable = true)
        @org.hibernate.annotations.Type(type = "corda-wrapper-binary")
        var constraintData: ByteArray? = null
    ) : PersistentState()

    @Entity
    @Table(
        name = "vault_linear_state",
        indexes = [Index(name = "external_id_index", columnList = "external_id"), Index(name = "uuid_index", columnList = "uuid")]
    )
    @Suppress("ForbiddenComment")
    class VaultLinearState(
        /** [net.corda.v5.ledger.contracts.ContractState] attributes */

        /**
         *  Represents a [net.corda.v5.ledger.contracts.LinearState] [UniqueIdentifier]
         */
        @Column(name = "external_id", nullable = true)
        var externalId: String?,

        // TODO: remove org.hibernate.annotations in API
        @Suppress("DEPRECATION")
        @Column(name = "uuid", nullable = false)
        @org.hibernate.annotations.Type(type = "uuid-char")
        var uuid: UUID
    ) : PersistentState() {
        constructor(uid: UniqueIdentifier) : this(externalId = uid.externalId, uuid = uid.id)
    }

    @Entity
    @Table(name = "vault_fungible_state")
    class VaultFungibleState(
        /** [net.corda.v5.ledger.contracts.FungibleState] attributes
         *
         *  Note: the underlying Product being issued must be modelled into the
         *  custom contract itself (eg. see currency in Cash contract state)
         */

        /** Amount attributes */
        @Column(name = "quantity", nullable = false)
        var quantity: Long,
    ) : PersistentState()

    @Entity
    @Table(name = "vault_transaction_note", indexes = [Index(name = "transaction_id_index", columnList = "transaction_id")])
    class VaultTxnNote(
        @Id
        @GeneratedValue
        @Column(name = "seq_no", nullable = false)
        var seqNo: Int,

        @Column(name = "transaction_id", length = 144, nullable = true)
        var txId: String?,

        @Column(name = "note", nullable = true)
        var note: String?
    ) {
        constructor(txId: String, note: String) : this(0, txId, note)
    }

    @Embeddable
    @Suppress("ForbiddenComment")
    // TODO: remove org.hibernate.annotations in API
    @org.hibernate.annotations.Immutable
    data class PersistentStateRefAndKey(/* Foreign key. */
        @Embedded override var stateRef: PersistentStateRef?,
        @Column(name = "public_key_hash", nullable = false) var publicKeyHash: String?
    ) : DirectStatePersistable, Serializable {
        constructor() : this(null, null)
    }

    @Entity
    @Table(name = "state_party", indexes = [Index(name = "state_party_idx", columnList = "public_key_hash")])
    class PersistentParty(
        @EmbeddedId
        override val compositeKey: PersistentStateRefAndKey,

        @Column(name = "x500_name", nullable = true)
        var x500Name: AbstractParty? = null
    ) : IndirectStatePersistable<PersistentStateRefAndKey> {
        constructor(stateRef: PersistentStateRef, abstractParty: AbstractParty) :
            this(PersistentStateRefAndKey(stateRef, abstractParty.owningKey.toStringShort()), abstractParty)
    }

    @Entity
    @Suppress("ForbiddenComment")
    // TODO: remove org.hibernate.annotations in API
    @org.hibernate.annotations.Immutable
    @Table(name = "v_pkey_hash_ex_id_map")
    class StateToExternalId(
        @EmbeddedId
        override val compositeKey: PersistentStateRefAndKey,

        // TODO: remove org.hibernate.annotations in API
        @Suppress("DEPRECATION", "ForbiddenComment")
        @Column(name = "external_id")
        @org.hibernate.annotations.Type(type = "uuid-char")
        val externalId: UUID
    ) : IndirectStatePersistable<PersistentStateRefAndKey>

    @Entity
    @Table(name = "vault_state_event", indexes = [Index(name = "vault_state_event_idx", columnList = "transaction_id,output_index")])
    @Suppress("MagicNumber")
    class DbVaultStateEvent(
        @Id
        @Column(name = "seq_no", nullable = false)
        var seqNo: Long,

        @Column(name = "transaction_id", length = 144, nullable = false)
        val txId: String,

        @Column(name = "output_index", length = 32, nullable = false)
        val outputIndex: Int,

        @Column(name = "event_timestamp", nullable = false)
        val timestamp: Instant,

        @Column(name = "event_type", nullable = false)
        val eventType: DbVaultEventType
    ) {

        enum class DbVaultEventType {
            // NB: Since values in the DB stored as integers, never remove or shuffle the values
            PRODUCE, CONSUME
        }

        override fun toString(): String {
            return "DbVaultStateEvent(seqNo=$seqNo, txId='$txId', outputIndex=$outputIndex, timestamp=$timestamp, eventType=$eventType)"
        }
    }

    data class SequentialStateEventView(
        val sequenceNumber: Long,
        val txId: String,
        val outputIndex: Int,
        val contractStateClassName: String,
        val eventTimestamp: Instant,
        val eventType: DbVaultStateEvent.DbVaultEventType,
        val notary: MemberX500Name,
        val relevancyStatus: RelevancyStatus,
        val stateStatus: StateStatus,
    ) {
        constructor(event: DbVaultStateEvent, state: VaultState) : this(
            event.seqNo, event.txId, event.outputIndex,
            state.contractStateClassName, event.timestamp, event.eventType, state.notary.name, state.relevancyStatus, state.stateStatus
        )
    }
}

/**
 * The maximum permissible size of contract constraint type data (for storage in vault states database table).
 *
 * This value establishes an upper limit of a CompositeKey with up to [MAX_NUMBER_OF_KEYS_IN_SIGNATURE_CONSTRAINT] keys stored in.
 * However, note this assumes a rather conservative upper bound per key.
 * For reference, measurements have shown the following numbers for each algorithm:
 * - 2048-bit RSA keys: 1 key -> 294 bytes, 2 keys -> 655 bytes, 3 keys -> 961 bytes
 * - 256-bit ECDSA (k1) keys: 1 key -> 88 bytes, 2 keys -> 231 bytes, 3 keys -> 331 bytes
 * - 256-bit EDDSA keys: 1 key -> 44 bytes, 2 keys -> 140 bytes, 3 keys -> 195 bytes
 */
const val MAX_CONSTRAINT_DATA_SIZE = 1_000 * MAX_NUMBER_OF_KEYS_IN_SIGNATURE_CONSTRAINT
