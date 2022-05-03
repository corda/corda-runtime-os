package net.corda.v5.ledger.services

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.LinearPointer
import net.corda.v5.ledger.contracts.LinearState
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.contracts.StatePointer
import net.corda.v5.ledger.contracts.StateRef
import net.corda.v5.ledger.contracts.StaticPointer
import net.corda.v5.ledger.contracts.TransactionResolutionException
import net.corda.v5.ledger.transactions.LedgerTransaction

@DoNotImplement
interface StateLoaderService {

    /**
     * Given a [StateRef] loads the referenced transaction and looks up the specified output [ContractState].
     *
     * __WARNING__ Do not use this method unless you really only want a single state - any batch loading should go through [load(Set<StateRef>)] as
     * repeatedly calling [load(StateRef)] can lead to repeat deserialization work and severe performance degradation.
     *
     * @param stateRef The [StateRef] to retrieve a [StateAndRef] with.
     *
     * @return The retrieved [StateAndRef].
     *
     * @throws TransactionResolutionException If the [stateRef] points to a non-existent transaction.
     */
    fun load(stateRef: StateRef): StateAndRef<ContractState>

    /**
     * Given a [Set] of [StateRef]'s loads each referenced transaction and looks up the specified output [ContractState]s.
     *
     * @param stateRefs The [StateRef]s to retrieve [StateAndRef]s with.
     *
     * @return The retrieved set of [StateAndRef]s.
     *
     * @throws TransactionResolutionException If any of the [stateRefs] point to a non-existent transaction.
     */
    fun load(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>>

    /**
     * Given a [List] of [StateRef]'s loads each referenced transaction and looks up the specified output [ContractState]s.
     *
     * The returned list will be in the same order as the input [stateRefs] list.
     *
     * @return The retrieved set of [StateAndRef]s.
     *
     * @throws TransactionResolutionException If any of the [stateRefs] point to a non-existent transaction.
     */
    fun loadOrdered(stateRefs: List<StateRef>): List<StateAndRef<ContractState>>

    /**
     * Resolves a [StatePointer] to a [StateAndRef] via a vault query. This method will either return a [StateAndRef]
     * or return an exception.
     *
     * @param pointer a [StatePointer] to resolve.
     * @throws IllegalStateException when the state is not found
     */
    fun <T : LinearState> load(pointer: LinearPointer<T>): StateAndRef<T>

    /**
     * Resolves a [StatePointer] to a [StateAndRef] via a vault query. This method will either return a [StateAndRef]
     * or return an exception.
     *
     * @param pointer a [StatePointer] to resolve.
     * @throws TransactionResolutionException when the state is not found
     */
    fun <T : ContractState> load(pointer: StaticPointer<T>): StateAndRef<T>

    /**
     * Resolves a [StatePointer] to a [StateAndRef] from inside a [LedgerTransaction]. The intuition here is that all
     * of the pointed-to states will be included in the transaction as reference states.
     *
     * @param pointer a [StatePointer] to resolve.
     * @param ltx the [LedgerTransaction] containing the [pointer] and pointed-to states.
     * @throws NoSuchElementException when the state is not found
     */
    fun <T : ContractState> load(pointer: StaticPointer<T>, ltx: LedgerTransaction): StateAndRef<T>

    /**
     * Resolves a [StatePointer] to a [StateAndRef] from inside a [LedgerTransaction]. The intuition here is that all
     * of the pointed-to states will be included in the transaction as reference states.
     *
     * @param pointer a [StatePointer] to resolve.
     * @param ltx the [LedgerTransaction] containing the [pointer] and pointed-to states.
     * @throws NoSuchElementException when the state is not found
     */
    fun <T : LinearState> load(pointer: LinearPointer<T>, ltx: LedgerTransaction): StateAndRef<T>
}