package net.corda.v5.ledger.services.vault

import net.corda.v5.application.persistence.query.GenericQueryPostProcessor
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.StateAndRef
import java.util.stream.Stream

/**
 * Post-processors can be implemented to enhance the Named Query API.
 *
 * [Stream]s are used for lazy operations to reduce memory footprint.
 *
 * After named query execution Corda attempts to load [StateAndRef]s from the query results and apply post-processing to the loaded states.
 * This interface ensures type safety by operating on a [Stream] of [StateAndRef] and returns a user defined stream of type [R].
 *
 * [name] is used to identify post-processors during the Named Query RPC API.
 */
interface StateAndRefPostProcessor<R> : GenericQueryPostProcessor<StateAndRef<ContractState>, R>