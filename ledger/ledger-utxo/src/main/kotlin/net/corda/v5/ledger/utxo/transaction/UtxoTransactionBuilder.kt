package net.corda.v5.ledger.utxo.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import java.security.PublicKey
import java.time.Instant

/**
 * Defines a builder for UTXO transactions.
 *
 * @property notary The transaction notary.
 */
@DoNotImplement
@CordaSerializable
@Suppress("TooManyFunctions")
interface UtxoTransactionBuilder {

    val notary: Party?

    /**
     * Adds an [Attachment] to the current [UtxoTransactionBuilder].
     *
     * @param attachmentId The ID of the [Attachment] to add to the current [UtxoTransactionBuilder].
     * @return Returns a new [UtxoTransactionBuilder] with an added [Attachment].
     */
    fun addAttachment(attachmentId: SecureHash): UtxoTransactionBuilder

    /**
     * Adds a command to the current [UtxoTransactionBuilder].
     *
     * @param command The command to add to the current [UtxoTransactionBuilder].
     * @return Returns a [UtxoTransactionBuilder] including the additional command.
     */
    fun addCommand(command: Command): UtxoTransactionBuilder

    /**
     * Adds signatories to the current [UtxoTransactionBuilder].
     *
     * @param signatories The signatories to add to the current [UtxoTransactionBuilder].
     * @return Returns a [UtxoTransactionBuilder] including the additional signatories.
     */
    fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder

    /**
     * Adds an input state to the current [UtxoTransactionBuilder].
     *
     * @param stateRef The [StateRef] of the input state to add to the current [UtxoTransactionBuilder].
     * @return Returns a [UtxoTransactionBuilder] including the additional input state.
     */
    fun addInputState(stateRef: StateRef): UtxoTransactionBuilder

    /**
     * Adds a reference input state to the current [UtxoTransactionBuilder].
     *
     * @param stateRef The [StateRef] of the reference input state to add to the current [UtxoTransactionBuilder].
     * @return Returns a [UtxoTransactionBuilder] including the additional reference input state.
     */
    fun addReferenceInputState(stateRef: StateRef): UtxoTransactionBuilder

    /**
     * Adds an output state to the current [UtxoTransactionBuilder].
     *
     * @param contractState The [ContractState] to add to the current [UtxoTransactionBuilder].
     * @return Returns a [UtxoTransactionBuilder] including the additional output state.
     */
    fun addOutputState(contractState: ContractState): UtxoTransactionBuilder

    /**
     * Adds the specified output states to the current [UtxoTransactionBuilder] as a tagged encumbrance group.
     *
     * @param tag The tag of the encumbrance group which the specified [ContractState] instances will belong to.
     * @param contractStates The [ContractState] instances to add to the current [UtxoTransactionBuilder].
     * @return Returns a [UtxoTransactionBuilder] including the encumbered output states.
     */
    fun addEncumberedOutputStates(tag: String, contractStates: Iterable<ContractState>): UtxoTransactionBuilder

    /**
     * Adds the specified output states to the current [UtxoTransactionBuilder] as a tagged encumbrance group.
     *
     * @param tag The tag of the encumbrance group which the specified [ContractState] instances will belong to.
     * @param contractStates The [ContractState] instances to add to the current [UtxoTransactionBuilder].
     * @return Returns a [UtxoTransactionBuilder] including the encumbered output states.
     */
    fun addEncumberedOutputStates(tag: String, vararg contractStates: ContractState): UtxoTransactionBuilder

    /**
     * Gets a list of encumbered [ContractState] instances from the specified encumbrance group tag.
     *
     * @param tag The encumbrance group tag for which to obtain the associated list of [ContractState] instances.
     * @return Returns a list of encumbered [ContractState] instances from the specified encumbrance group tag.
     * @throws IllegalArgumentException if the encumbrance group tag does not exist.
     */
    fun getEncumbranceGroup(tag: String): List<ContractState>

    /**
     * Gets a map of encumbrance group tags and the associated encumbered [ContractState] instances.
     *
     * @return Returns map of encumbrance group tags and the associated encumbered [ContractState] instances.
     */
    fun getEncumbranceGroups(): Map<String, List<ContractState>>

    /**
     * Sets the [Party] as a notary to the current [UtxoTransactionBuilder].
     *
     * @param notary The [Party] to set as a notary to the current [UtxoTransactionBuilder].
     * @return Returns a new [UtxoTransactionBuilder] with the new notary.
     */
    fun setNotary(notary: Party): UtxoTransactionBuilder

    /**
     * Sets the transaction time window to be valid until the specified [Instant], tending towards negative infinity.
     *
     * @param until The [Instant] until which the transaction time window is valid.
     * @return Returns a [UtxoTransactionBuilder] including the specified time window.
     */
    fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder

    /**
     * Sets the transaction time window to be valid between the specified [Instant] values.
     *
     * @param from The [Instant] from which the transaction time window is valid.
     * @param until The [Instant] until which the transaction time window is valid.
     * @return Returns a [UtxoTransactionBuilder] including the specified time window.
     */
    fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder

    /**
     * Signs the transaction with any required signatories that belong to the current node.
     *
     * @return Returns a [UtxoSignedTransaction] with signatures for any required signatories that belong to the current node.
     *
     * @throws IllegalStateException when called a second time on the same object to prevent
     *      unintentional duplicate transactions.
     */
    @Suspendable
    fun toSignedTransaction(): UtxoSignedTransaction

    /**
     * Signs the transaction with the specified signatory key.
     *
     * @param signatory The signatory expected to sign the current transaction.
     * @return Returns a [UtxoSignedTransaction] with signature for the specified signatory key.
     *
     * @throws IllegalStateException when called a second time on the same object to prevent
     *      unintentional duplicate transactions.
     */
    @Suspendable
    @Deprecated("Temporary function until the argumentless version gets available")
    fun toSignedTransaction(signatory: PublicKey): UtxoSignedTransaction
}
