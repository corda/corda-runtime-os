package net.corda.v5.ledger.obsolete.contracts

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.obsolete.contracts.TransactionVerificationException.TransactionContractConflictException
import net.corda.v5.ledger.obsolete.transactions.LedgerTransaction
import kotlin.reflect.KClass

typealias ContractClassName = String

/**
 * Implemented by a program that implements business logic on the shared ledger. All participants run this code for
 * every [LedgerTransaction][net.corda.v5.ledger.obsolete.transactions.LedgerTransaction] they see on the network, for every input and output state. All
 * contracts must accept the transaction for it to be accepted: failure of any aborts the entire thing. The time is taken
 * from a trusted time-window attached to the transaction itself i.e. it is NOT necessarily the current time.
 *
 * TODO Contract serialization is likely to change, so the annotation is likely temporary.
 */
@CordaSerializable
interface Contract {
    /**
     * Takes an object that represents a state transition, and ensures the inputs/outputs/commands make sense.
     * Must throw an exception if there's a problem that should prevent state transition. Takes a single object
     * rather than an argument so that additional data can be added without breaking binary compatibility with
     * existing contract code.
     *
     * @throws IllegalArgumentException The [verify] implementations typically throw [IllegalArgumentException]s.
     */
    fun verify(tx: LedgerTransaction)
}

/**
 * This annotation is required by any [ContractState] which needs to ensure that it is only ever processed as part of a
 * [TransactionState] referencing the specified [Contract]. It may be omitted in the case that the [ContractState] class
 * is defined as an inner class of its owning [Contract] class, in which case the "X belongs to Y" relationship is taken
 * to be implicitly declared.
 *
 * During verification of transactions, prior to their being written into the ledger, all input and output states are
 * checked to ensure that their [ContractState]s match with their [Contract]s as specified either by this annotation, or
 * by their inner/outer class relationship.
 *
 * The transaction will write a warning to the log (for corDapps with a target version less than 4) or
 * fail with a [TransactionContractConflictException] if any mismatch is detected.
 *
 * @param value The class of the [Contract] to which states of the annotated [ContractState] belong.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BelongsToContract(val value: KClass<out Contract>)

/** The annotated [Contract] implements the legal prose identified by the given URI. */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class LegalProseReference(val uri: String)