package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.time.Instant
import java.util.Objects

class UtxoBaselinedTransactionBuilder
private constructor(
    val baselineTransactionBuilder: UtxoTransactionBuilderInternal,
    private val currentTransactionBuilder: UtxoTransactionBuilderInternal,
) : UtxoTransactionBuilderInternal by currentTransactionBuilder {

    constructor(transactionBuilderInternal: UtxoTransactionBuilderInternal) : this(
        transactionBuilderInternal.copy(),
        transactionBuilderInternal
    )

    override fun setNotary(notary: Party): UtxoTransactionBuilder {
        require(this.notary == null || this.notary == notary) {
            "Original notary cannot be overridden."
        }
        currentTransactionBuilder.setNotary(notary)
        return this
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        val timeWindow = TimeWindowUntilImpl(until)
        require(this.timeWindow == null || this.timeWindow == timeWindow) {
            "Original time window cannot be overridden."
        }
        currentTransactionBuilder.setTimeWindowUntil(timeWindow.until)
        return this
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
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
                "notary=$notary (orig: ${baselineTransactionBuilder.notary}), " +
                "timeWindow=$timeWindow (orig: ${baselineTransactionBuilder.timeWindow}), " +
                "attachments=$attachments (orig: ${baselineTransactionBuilder.attachments}), " +
                "commands=$commands (orig: ${baselineTransactionBuilder.commands}), " +
                "signatories=$signatories (orig: ${baselineTransactionBuilder.signatories}), " +
                "inputStateRefs=$inputStateRefs (orig: ${baselineTransactionBuilder.inputStateRefs}), " +
                "referenceStateRefs=$referenceStateRefs (orig: ${baselineTransactionBuilder.referenceStateRefs}), " +
                "outputStates=$outputStates (orig: ${baselineTransactionBuilder.outputStates})" +
                ")"
    }
}