package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

@Suppress("TooManyFunctions")
class UtxoBaselinedTransactionBuilder private constructor(
    val baselineTransactionBuilder: UtxoTransactionBuilderContainer,
    private val currentTransactionBuilder: UtxoTransactionBuilderInternal,
) : UtxoTransactionBuilderInternal {

    constructor(transactionBuilderInternal: UtxoTransactionBuilderInternal) : this(
        transactionBuilderInternal.copy(),
        transactionBuilderInternal
    )

    override val timeWindow: TimeWindow?
        get() = currentTransactionBuilder.timeWindow
    override val attachments: List<SecureHash>
        get() = currentTransactionBuilder.attachments
    override val commands: List<Command>
        get() = currentTransactionBuilder.commands
    override val signatories: List<PublicKey>
        get() = currentTransactionBuilder.signatories
    override val inputStateRefs: List<StateRef>
        get() = currentTransactionBuilder.inputStateRefs
    override val referenceStateRefs: List<StateRef>
        get() = currentTransactionBuilder.referenceStateRefs
    override val outputStates: List<ContractStateAndEncumbranceTag>
        get() = currentTransactionBuilder.outputStates

    override fun getNotary(): Party? = currentTransactionBuilder.notary

    override fun setNotary(notary: Party): UtxoBaselinedTransactionBuilder {
        require(this.notary == null || this.notary == notary) {
            "Original notary cannot be overridden."
        }
        currentTransactionBuilder.setNotary(notary)
        return this
    }

    override fun setTimeWindowUntil(until: Instant): UtxoBaselinedTransactionBuilder {
        val timeWindow = TimeWindowUntilImpl(until)
        require(this.timeWindow == null || this.timeWindow == timeWindow) {
            "Original time window cannot be overridden."
        }
        currentTransactionBuilder.setTimeWindowUntil(timeWindow.until)
        return this
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoBaselinedTransactionBuilder {
        val timeWindow = TimeWindowBetweenImpl(from, until)
        require(this.timeWindow == null || this.timeWindow == timeWindow) {
            "Original time window cannot be overridden."
        }
        currentTransactionBuilder.setTimeWindowBetween(timeWindow.from, timeWindow.until)
        return this
    }

    @Suspendable
    override fun toSignedTransaction(): UtxoSignedTransaction {
        throw UnsupportedOperationException(
            "Transaction builder proposals are supposed to be returned to their originator. Their signing is not supported."
        )
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        return this === other
                || other is UtxoBaselinedTransactionBuilder
                && other.baselineTransactionBuilder == baselineTransactionBuilder
                && other.notary == notary
                && other.timeWindow == timeWindow
                && other.attachments == attachments
                && other.commands == commands
                && other.inputStateRefs == inputStateRefs
                && other.referenceStateRefs == referenceStateRefs
                && other.outputStates == outputStates
                && other.signatories == signatories
    }

    override fun hashCode(): Int = Objects.hash(
        baselineTransactionBuilder,
        notary,
        timeWindow,
        attachments,
        commands,
        signatories,
        inputStateRefs,
        referenceStateRefs,
        outputStates
    )

    override fun toString(): String {
        return "UtxoBaselinedTransactionBuilder(" +
                "notary=$notary (orig: ${baselineTransactionBuilder.getNotary()}), " +
                "timeWindow=$timeWindow (orig: ${baselineTransactionBuilder.timeWindow}), " +
                "attachments=$attachments (orig: ${baselineTransactionBuilder.attachments}), " +
                "commands=$commands (orig: ${baselineTransactionBuilder.commands}), " +
                "signatories=$signatories (orig: ${baselineTransactionBuilder.signatories}), " +
                "inputStateRefs=$inputStateRefs (orig: ${baselineTransactionBuilder.inputStateRefs}), " +
                "referenceStateRefs=$referenceStateRefs (orig: ${baselineTransactionBuilder.referenceStateRefs}), " +
                "outputStates=$outputStates (orig: ${baselineTransactionBuilder.outputStates})" +
                ")"
    }

    // Unfortunately we cannot just simply delegate everything to currentTransactionBuilder since that would return itself
    // instead of the baselined transaction builder object.

    override fun addAttachment(attachmentId: SecureHash): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addAttachment(attachmentId)
        return this
    }

    override fun addCommand(command: Command): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addCommand(command)
        return this
    }

    override fun addSignatories(signatories: MutableIterable<PublicKey>): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addSignatories(signatories)
        return this
    }

    override fun addSignatories(vararg signatories: PublicKey?): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addSignatories(signatories.toList())
        return this
    }

    override fun addInputState(stateRef: StateRef): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addInputState(stateRef)
        return this
    }

    override fun addInputStates(stateRefs: MutableIterable<StateRef>): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addInputStates(stateRefs)
        return this
    }

    override fun addInputStates(vararg stateRefs: StateRef?): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addInputStates(stateRefs.toList())
        return this
    }

    override fun addReferenceState(stateRef: StateRef): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addReferenceState(stateRef)
        return this
    }

    override fun addReferenceStates(stateRefs: MutableIterable<StateRef>): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addReferenceStates(stateRefs)
        return this
    }

    override fun addReferenceStates(vararg stateRefs: StateRef?): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addReferenceStates(stateRefs.toList())
        return this
    }

    override fun addOutputState(contractState: ContractState): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addOutputState(contractState)
        return this
    }

    override fun addOutputStates(contractStates: MutableIterable<ContractState>): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addOutputStates(contractStates)
        return this
    }

    override fun addOutputStates(vararg contractStates: ContractState?): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addOutputStates(contractStates.toList())
        return this
    }

    override fun addEncumberedOutputStates(
        tag: String,
        contractStates: MutableIterable<ContractState>
    ): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addEncumberedOutputStates(tag, contractStates)
        return this
    }

    override fun addEncumberedOutputStates(tag: String, vararg contractStates: ContractState?): UtxoBaselinedTransactionBuilder {
        currentTransactionBuilder.addEncumberedOutputStates(tag, contractStates.toList())
        return this
    }

    override fun copy(): UtxoTransactionBuilderContainer =
        currentTransactionBuilder.copy()

    override fun append(other: UtxoTransactionBuilderContainer) =
        currentTransactionBuilder.append(other)

    override fun getEncumbranceGroup(tag: String) = currentTransactionBuilder.getEncumbranceGroup(tag)
    override fun getEncumbranceGroups(): MutableMap<String, MutableList<ContractState>> =
        currentTransactionBuilder.encumbranceGroups

    /**
     * Calculates what got added to a transaction builder comparing to the baseline.
     * Notary and TimeWindow changes are not considered if the original had them set already.
     * This gives precedence to those original values.
     * We cannot use list minus on commands and output states since those are user defined therefore there
     * are no guarantees that value semantics has been implemented on them.
     */
    fun diff(): UtxoTransactionBuilderContainer =
        UtxoTransactionBuilderContainer(
            if (baselineTransactionBuilder.getNotary() == null) notary else null,
            if (baselineTransactionBuilder.timeWindow == null) timeWindow else null,
            attachments - baselineTransactionBuilder.attachments.toSet(),
            commands.drop(baselineTransactionBuilder.commands.size),
            signatories - baselineTransactionBuilder.signatories.toSet(),
            inputStateRefs - baselineTransactionBuilder.inputStateRefs.toSet(),
            referenceStateRefs - baselineTransactionBuilder.referenceStateRefs.toSet(),
            outputStates.drop(baselineTransactionBuilder.outputStates.size)
        )
}