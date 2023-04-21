package net.corda.ledger.utxo.testkit

import net.corda.v5.ledger.utxo.Command

data class UtxoCommandExample(val commandArgument: String? = null) : Command{
    override fun equals(other: Any?): Boolean =
        (this === other) || (
                other is UtxoCommandExample &&
                        commandArgument == other.commandArgument
                )

    override fun hashCode(): Int = commandArgument.hashCode()
}