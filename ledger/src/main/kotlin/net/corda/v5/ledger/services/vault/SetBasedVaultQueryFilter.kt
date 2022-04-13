package net.corda.v5.ledger.services.vault

import net.corda.v5.application.persistence.query.NamedQueryFilter
import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant

@CordaSerializable
data class SetBasedVaultQueryFilter(
    val txIds: Set<String>?,
    val startTimestamp: Instant?,
    val endTimestamp: Instant?,
    val eventTypes: Set<VaultEventType>?,
    val contractStateClassNames: Set<String>?,
    val relevancyStatuses: Set<RelevancyStatus>?,
    val stateStatuses: Set<StateStatus>?
) : NamedQueryFilter {

    init {
        if (txIds != null)
            require(txIds.isNotEmpty()) { "Request to filter to an empty set of txIds - must be null or a finite set." }
        if (eventTypes != null)
            require(eventTypes.isNotEmpty()) { "Request to filter to an empty set of eventTypes - must be null or a finite set." }
        if (contractStateClassNames != null)
            require(contractStateClassNames.isNotEmpty()) { "Request to filter to an empty set of contractStateClassNames - must be null or a finite set." }
        if (relevancyStatuses != null)
            require(relevancyStatuses.isNotEmpty()) { "Request to filter to an empty set of relevancyStatuses - must be null or a finite set." }
        if (stateStatuses != null)
            require(stateStatuses.isNotEmpty()) { "Request to filter to an empty set of stateStatuses - must be null or a finite set." }
    }

    constructor()
            : this(null, null, null, null, null, null, null)

    constructor(txIds: Set<String>?)
            : this(txIds, null, null, null, null, null, null)

    constructor(txIds: Set<String>?, startTimestamp: Instant?)
            : this(txIds, startTimestamp, null, null, null, null, null)

    constructor(txIds: Set<String>?, startTimestamp: Instant?, endTimestamp: Instant?)
            : this(txIds, startTimestamp, endTimestamp, null, null, null, null)

    constructor(txIds: Set<String>?, startTimestamp: Instant?, endTimestamp: Instant?, eventTypes: Set<VaultEventType>?)
            : this(txIds, startTimestamp, endTimestamp, eventTypes, null, null, null)

    constructor(
        txIds: Set<String>?,
        startTimestamp: Instant?,
        endTimestamp: Instant?,
        eventTypes: Set<VaultEventType>?,
        contractStateClassNames: Set<String>?
    )
            : this(txIds, startTimestamp, endTimestamp, eventTypes, contractStateClassNames, null, null)

    constructor(
        txIds: Set<String>?,
        startTimestamp: Instant?,
        endTimestamp: Instant?,
        eventTypes: Set<VaultEventType>?,
        contractStateClassNames: Set<String>?,
        relevancyStatuses: Set<RelevancyStatus>?
    )
            : this(txIds, startTimestamp, endTimestamp, eventTypes, contractStateClassNames, relevancyStatuses, null)

    class Builder {

        private var txIds: Set<String>? = null
        private var startTimestamp: Instant? = null
        private var endTimestamp: Instant? = null
        private var eventTypes: Set<VaultEventType>? = null
        private var contractStateClassNames: Set<String>? = null
        private var relevancyStatuses: Set<RelevancyStatus>? = null
        private var stateStatuses: Set<StateStatus>? = null

        fun build(): SetBasedVaultQueryFilter =
            SetBasedVaultQueryFilter(txIds, startTimestamp, endTimestamp, eventTypes, contractStateClassNames, relevancyStatuses, stateStatuses)

        fun withTxIds(txIds: Set<String>): Builder {
            require(txIds.isNotEmpty()) { "Request to filter to an empty set of txIds - must be null or a finite set." }
            return apply { this.txIds = txIds }
        }

        fun withStartTimestamp(startTimestamp: Instant): Builder = apply { this.startTimestamp = startTimestamp }

        fun withEndTimestamp(endTimestamp: Instant): Builder = apply { this.endTimestamp = endTimestamp }

        fun withEventTypes(eventTypes: Set<VaultEventType>): Builder {
            require(eventTypes.isNotEmpty()) { "Request to filter to an empty set of eventTypes - must be null or a finite set." }
            return apply { this.eventTypes = eventTypes }
        }

        fun withContractStateClassNames(contractStateClassNames: Set<String>): Builder {
            require(contractStateClassNames.isNotEmpty()) { "Request to filter to an empty set of contractStateClassNames - must be null or a finite set." }
            return apply { this.contractStateClassNames = contractStateClassNames }
        }

        fun withRelevancyStatuses(relevancyStatuses: Set<RelevancyStatus>): Builder {
            require(relevancyStatuses.isNotEmpty()) { "Request to filter to an empty set of relevancyStatuses - must be null or a finite set." }
            return apply { this.relevancyStatuses = relevancyStatuses }
        }

        fun withStateStatuses(stateStatuses: Set<StateStatus>): Builder {
            require(stateStatuses.isNotEmpty()) { "Request to filter to an empty set of stateStatuses - must be null or a finite set." }
            return apply { this.stateStatuses = stateStatuses }
        }
    }
}

