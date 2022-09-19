package net.corda.ledger.utxo.impl

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef

/**
 * Represents a reference to a state.
 *
 * @constructor Creates a new instance of the [StateRefImpl] data class.
 * @property transactionHash The hash of the transaction in which the referenced state was created.
 * @property index The index of the state in the transaction's outputs in which the referenced state was created.
 */
@CordaSerializable
data class StateRefImpl(override val transactionHash: SecureHash, override val index: Int) : StateRef {

    /**
     * @property DELIMITER Represents the delimiter that is used to split the transaction hash and index.
     */
    companion object {

        /**
         * Parses the specified value into a [StateRef].
         *
         * @param value The value to parse.
         * @return Returns a [StateRef] representing the specified value.
         */
        @JvmStatic
        fun parse(value: String): StateRef {
            try {
                val transactionHash = value.substringBeforeLast(DELIMITER)
                val index = value.substringAfterLast(DELIMITER)
                return StateRefImpl(SecureHash.parse(transactionHash), index.toInt())
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "The hash component of the specified value could not be parsed into a StateRef: $value.", ex
                )
            } catch (ex: NumberFormatException) {
                throw IllegalArgumentException(
                    "The index component of the specified value could not be parsed into a StateRef: $value.", ex
                )
            }
        }

        private const val DELIMITER = ':'
    }

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    override fun toString(): String = "$transactionHash$DELIMITER$index"
}
