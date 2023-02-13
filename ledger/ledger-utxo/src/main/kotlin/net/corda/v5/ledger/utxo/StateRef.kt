package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

/**
 * Defines a reference to a [ContractState].
 *
 * @property index The index of the state in the transaction's outputs in which the referenced state was created.
 * @property transactionId The id of the transaction in which the referenced state was created.
 */
@CordaSerializable
data class StateRef(val transactionId: SecureHash, val index: Int) {

    companion object {

        /**
         * Parses the specified value into a new [StateRef] instance.
         *
         * @param value The value to parse into a [StateRef] instance.
         * @return Returns a new [StateRef] instance.
         * @throws IllegalArgumentException if either the transaction id, or index components cannot be parsed.
         */
        @JvmStatic
        fun parse(value: String): StateRef {
            return try {
                val transactionHash = SecureHash.parse(value.substringBeforeLast(":"))
                val index = value.substringAfterLast(":").toInt()

                StateRef(transactionHash, index)
            } catch (ex: NumberFormatException) {
                throw IllegalArgumentException(
                    "Failed to parse a StateRef from the specified value. The index is malformed: $value.",
                    ex
                )
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Failed to parse a StateRef from the specified value. The transaction id is malformed: $value.",
                    ex
                )
            }
        }
    }

    /**
     * Gets the [String] representation of the current object.
     *
     * @return Returns the [String] representation of the current object.
     */
    override fun toString(): String {
        return "$transactionId:$index"
    }
}
