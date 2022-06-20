package net.corda.v5.ledger.obsolete.schemas

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.obsolete.contracts.ContractState
import net.corda.v5.ledger.obsolete.contracts.StateRef
import net.corda.v5.persistence.MappedSchema
import org.hibernate.annotations.Immutable
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.MappedSuperclass

//DOCSTART QueryableState
/**
 * A contract state that may be mapped to database schemas configured for this node to support querying for,
 * or filtering of, states.
 */
interface QueryableState : ContractState {
    /**
     * Enumerate the schemas this state can export representations of itself as.
     */
    fun supportedSchemas(): Iterable<MappedSchema>

    /**
     * Export a representation for the given schema.
     */
    fun generateMappedObject(schema: MappedSchema): PersistentState
}
//DOCEND QueryableState


/**
 * A super class for all mapped states exported to a schema that ensures the [StateRef] appears on the database row.  The
 * [StateRef] will be set to the correct value by the framework (there's no need to set during mapping generation by the state itself).
 */
@MappedSuperclass
@CordaSerializable
open class PersistentState(@EmbeddedId override var stateRef: PersistentStateRef?) : DirectStatePersistable {

    constructor() : this(stateRef = null)
}

/**
 * Embedded [StateRef] representation used in state mapping.
 */
@Embeddable
@Immutable
data class PersistentStateRef(
        @Suppress("MagicNumber") // column width
        @Column(name = "transaction_id", length = 144, nullable = false)
        var txId: String,

        @Column(name = "output_index", nullable = false)
        var index: Int
) : Serializable {
    constructor(stateRef: StateRef) : this(stateRef.txhash.toString(), stateRef.index)
}

/**
 * Marker interface to denote a persistable Corda state entity that will always have a transaction id and index
 */
interface StatePersistable

/**
 * Marker interface to denote a persistable Corda state entity that exposes the transaction id and index as composite key called [stateRef].
 */
interface DirectStatePersistable : StatePersistable {
    val stateRef: PersistentStateRef?
}

/**
 * Marker interface to denote a persistable Corda state entity that exposes the transaction id and index as a nested composite key called [compositeKey]
 * that is itself a [DirectStatePersistable].  i.e. exposes a [stateRef].
 */
interface IndirectStatePersistable<T : DirectStatePersistable> : StatePersistable {
    val compositeKey: T
}
