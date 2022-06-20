package net.corda.v5.ledger.obsolete.transactions

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.obsolete.contracts.Command
import net.corda.v5.ledger.obsolete.contracts.TimeWindow
import net.corda.v5.ledger.obsolete.crypto.TransactionDigestAlgorithmNames

/**
 * Implemented by [WireTransaction] and [FilteredTransaction]. A TraversableTransaction allows you to iterate
 * over the flattened components of the underlying transaction structure, taking into account that some
 * may be missing in the case of this representing a "torn" transaction. Please see the user guide section
 * "Transaction tear-offs" to learn more about this feature.
 */
@DoNotImplement
interface TraversableTransaction : CoreTransaction {

    val componentGroups: List<ComponentGroup>

    /** the set of digest algorithms and functions used to label the transaction Merkle tree leaves and nodes */
    val transactionDigestAlgorithmNames: TransactionDigestAlgorithmNames

    /** Hashes of the ZIP/JAR files that are needed to interpret the contents of this wire transaction. */
    val attachments: List<SecureHash>

    /** Ordered list of ([CommandData][net.corda.v5.ledger.obsolete.contracts.CommandData], [PublicKey][java.security.PublicKey])
     * pairs that instruct the contracts what to do. */
    val commands: List<Command<*>>

    val timeWindow: TimeWindow?

    /**
     * Returns a list of all the component groups that are present in the transaction, excluding the privacySalt,
     * in the following order (which is the same with the order in [ComponentGroupEnum][net.corda.v5.ledger.obsolete.contracts.ComponentGroupEnum]:
     * - list of each input that is present
     * - list of each output that is present
     * - list of each command that is present
     * - list of each attachment that is present
     * - The notary [Party][net.corda.v5.application.identity.Party], if present (list with one element)
     * - The time-window of the transaction, if present (list with one element)
     * - list of each reference input that is present
     * - group parameters hash if present
     */
    val availableComponentGroups: List<List<Any>>
}