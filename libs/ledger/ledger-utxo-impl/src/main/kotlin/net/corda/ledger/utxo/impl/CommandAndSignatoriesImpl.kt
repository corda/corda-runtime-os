package net.corda.ledger.utxo.impl

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.CommandAndSignatories
import java.security.PublicKey

/**
 * Represents a command, and the signatories associated with the specified command.
 *
 * @property command The command to verify.
 * @property signatories The signatory signing keys associated with the specified command.
 */
data class CommandAndSignatoriesImpl<T : Command>(
    override val command: T,
    override val signatories: Set<PublicKey>
) : CommandAndSignatories<T>