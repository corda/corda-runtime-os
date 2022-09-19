package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import java.security.PublicKey
import java.util.*
import kotlin.reflect.KClass

/**
 * Indicates the [Contract] that the current state belongs to.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BelongsToContract(val value: KClass<out Contract>)

/**
 * Indicates the URI that references the legal prose associated with the current [Contract].
 */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class LegalProseReference(val value: String)

/**
 * Defines a contract state.
 *
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions.
 *
 * States are immutable. Once created they are never updated, instead, any changes must generate a new successor state.
 * States can be updated (consumed) only once. The notary is responsible for ensuring there is no "double spending" by
 * only signing a transaction if the input states are all free.
 *
 * @property participants The public keys of any participants associated with the current contract state.
 */
@CordaSerializable
interface ContractState {
    val participants: Set<PublicKey>
}

/**
 * Defines a contract state that is identifiable.
 *
 * The expectation is that identifiable state implementations will evolve by superseding previous versions of
 * themselves whilst sharing a common identifier (id).
 *
 * @property id The identifier of the current state.
 */
interface IdentifiableState : ContractState {
    val id: UUID
}

/**
 * Defines a contract state that is fungible.
 *
 * The expectation is that fungible state implementations can be split, merged, and are mutually exchangeable with
 * other instances of the same fungible state type.
 *
 * @param T The underlying numeric type of the quantity.
 * @property quantity The quantity of the current state.
 */
interface FungibleState<T : Number> : ContractState {
    val quantity: T
}

/**
 * Defines a contract state that is issuable.
 *
 * The expectation is that issuable state implementations will require that the issuer signs over any transaction
 * where the state is being issued, and/or consumed, without producing an update.
 *
 * @property issuer The public key of the issuer of the current state.
 */
interface IssuableState : ContractState {
    val issuer: PublicKey
}

/**
 * Defines a contract state that is bearable (i.e. a "bearer instrument").
 *
 * The expectation is that bearable state implementations will require a single bearer who will be responsible for
 * the ownership of the state, and will be responsible, or have the right to perform certain actions over the state.
 *
 * @property bearer The public key of the bearer of the current state.
 */
interface BearableState : ContractState {
    val bearer: PublicKey
}
