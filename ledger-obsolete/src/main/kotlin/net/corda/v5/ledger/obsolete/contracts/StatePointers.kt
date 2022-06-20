package net.corda.v5.ledger.obsolete.contracts

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.obsolete.UniqueIdentifier
import net.corda.v5.ledger.obsolete.services.StateLoaderService

/**
 * A [StatePointer] contains a [pointer] to a [ContractState]. The [StatePointer] can be included in a [ContractState]
 * or included in an off-ledger data structure. [StatePointer]s can be resolved to a [StateAndRef] by performing a
 * vault query. There are two types of pointers; linear and static. [LinearPointer]s are for use with [LinearState]s.
 * [StaticPointer]s are for use with any type of [ContractState].
 */
@CordaSerializable
@DoNotImplement
interface StatePointer<T : ContractState> {

    /**
     * An identifier for the [ContractState] that this [StatePointer] points to.
     */
    val pointer: Any

    /**
     * Type of the state which is being pointed to.
     */
    val type: Class<T>

    /**
     * Determines whether the state pointer should be resolved to a reference input when used in a transaction.
     */
    val isResolved: Boolean
}

/**
 * A [StaticPointer] contains a [pointer] to a specific [StateRef] and can be resolved by looking up the [StateRef] via
 * [StateLoaderService]. There are a number of things to keep in mind when using [StaticPointer]s:
 * - The [ContractState] being pointed to may be spent or unspent when the [pointer] is resolved
 * - The [ContractState] may not be known by the node performing the look-up in which case the [resolve] method will
 *   throw a [TransactionResolutionException]
 */
class StaticPointer<T : ContractState>(
    override val pointer: StateRef,
    override val type: Class<T>,
    override val isResolved: Boolean
) : StatePointer<T> {

    constructor(pointer: StateRef, type: Class<T>) : this(pointer, type, isResolved = false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StaticPointer<*>) return false

        if (pointer != other.pointer) return false

        return true
    }

    override fun hashCode(): Int {
        return pointer.hashCode()
    }
}

/**
 * Creates a [StaticPointer] to the specified contract state.
 *
 * @param stateAndRef The [StateAndRef] instance from which to construct a static pointer.
 * @param isResolved Determines whether the state pointer should be resolved to a reference input when included in a transaction.
 * @return Returns a [StaticPointer] to the specified contract state.
 */
inline fun <reified T : ContractState> staticPointer(stateAndRef: StateAndRef<T>, isResolved: Boolean): StaticPointer<T> {
    return StaticPointer(stateAndRef.ref, T::class.java, isResolved)
}

/**
 * Creates a [StaticPointer] to the specified contract state.
 *
 * @param stateAndRef The [StateAndRef] instance from which to construct a static pointer.
 * @return Returns a [StaticPointer] to the specified contract state.
 */
inline fun <reified T : ContractState> staticPointer(stateAndRef: StateAndRef<T>): StaticPointer<T> {
    return StaticPointer(stateAndRef.ref, T::class.java, isResolved = false)
}

/**
 * [LinearPointer] allows a [ContractState] to "point" to another [LinearState] creating a "many-to-one" relationship
 * between all the states containing the pointer to a particular [LinearState] and the [LinearState] being pointed to.
 * Using the [LinearPointer] is useful when one state depends on the data contained within another state that evolves
 * independently. When using [LinearPointer] it is worth noting:
 * - The node performing the resolution may not have seen any [LinearState]s with the specified [linearId], as such the
 *   vault query in [resolve] will return null and [resolve] will throw an exception
 * - The node performing the resolution may not have the latest version of the [LinearState] and therefore will return
 *   an older version of the [LinearState]. As the pointed-to state will be added as a reference state to the transaction
 *   then the transaction with such a reference state cannot be committed to the ledger until the most up-to-date version
 *   of the [LinearState] is available. See reference states documentation on docs.corda.net for more info.
 */
class LinearPointer<T : LinearState>(
    override val pointer: UniqueIdentifier,
    override val type: Class<T>,
    override val isResolved: Boolean
) : StatePointer<T> {

    constructor(pointer: UniqueIdentifier, type: Class<T>) : this(pointer, type, isResolved = true)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LinearPointer<*>) return false

        if (pointer != other.pointer) return false

        return true
    }

    override fun hashCode(): Int {
        return pointer.hashCode()
    }
}

/**
 * Creates a [LinearPointer] to the specified linear state.
 *
 * @param state The [LinearState] instance from which to construct a linear pointer.
 * @param isResolved Determines whether the state pointer should be resolved to a reference input when included in a transaction.
 * @return Returns a [LinearPointer] to the specified linear state.
 */
inline fun <reified T : LinearState> linearPointer(state: T, isResolved: Boolean): LinearPointer<T> {
    return LinearPointer(state.linearId, T::class.java, isResolved)
}

/**
 * Creates a [LinearPointer] to the specified linear state.
 *
 * @param state The [LinearState] instance from which to construct a linear pointer.
 * @return Returns a [LinearPointer] to the specified linear state.
 */
inline fun <reified T : LinearState> linearPointer(state: T): LinearPointer<T> {
    return LinearPointer(state.linearId, T::class.java, isResolved = true)
}