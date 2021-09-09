package net.corda.v5.ledger.services.vault.events

import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.services.vault.VaultEventType
import java.time.Instant
import net.corda.v5.base.annotations.DoNotImplement

/**
 * [VaultStateEvent] contains information about state changes to the vault.
 */
@DoNotImplement
interface VaultStateEvent<T: ContractState> {

    /**
     * Get the [StateAndRef] that triggered this event.
     */
    val stateAndRef: StateAndRef<T>

    /**
     * Gets the [VaultEventType] that the event's [StateAndRef] was recorded with.
     */
    val eventType: VaultEventType

    /**
     * Gets the time that this event's [StateAndRef] was recorded.
     */
    val timestamp: Instant
}