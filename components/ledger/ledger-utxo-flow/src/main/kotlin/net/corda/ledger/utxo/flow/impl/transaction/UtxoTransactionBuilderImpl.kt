package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoTransactionBuilderVerifier
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

@Suppress("TooManyFunctions", "LongParameterList")
class UtxoTransactionBuilderImpl(
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    private val notaryLookup: NotaryLookup,
    private var notaryName: MemberX500Name? = null,
    private var notaryKey: PublicKey? = null,
    override var timeWindow: TimeWindow? = null,
    override val commands: MutableList<Command> = mutableListOf(),
    override val signatories: MutableList<PublicKey> = mutableListOf(),
    override val inputStateRefs: MutableList<StateRef> = mutableListOf(),
    override val referenceStateRefs: MutableList<StateRef> = mutableListOf(),
    override val outputStates: MutableList<ContractStateAndEncumbranceTag> = mutableListOf()
) : UtxoTransactionBuilderInternal {

    private var alreadySigned = false

    override fun addCommand(command: Command): UtxoTransactionBuilder {
        this.commands += command
        return this
    }

    override fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        require(
            this.signatories.intersect(signatories.toSet()).isEmpty() &&
                signatories.distinct().size == signatories.count()
        ) {
            "Duplicating signatories are not allowed."
        }
        this.signatories += signatories
        return this
    }

    override fun addSignatories(vararg signatories: PublicKey): UtxoTransactionBuilder {
        return addSignatories(signatories.toList())
    }

    override fun addInputState(stateRef: StateRef): UtxoTransactionBuilder {
        require(stateRef !in inputStateRefs) {
            "Duplicating input StateRefs is not allowed."
        }
        this.inputStateRefs += stateRef
        return this
    }

    override fun addInputStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        require(
            this.inputStateRefs.intersect(stateRefs.toSet()).isEmpty() &&
                stateRefs.distinct().size == stateRefs.count()
        ) {
            "Duplicating input StateRefs is not allowed."
        }
        this.inputStateRefs += stateRefs
        return this
    }

    override fun addInputStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return addInputStates(stateRefs.toList())
    }

    override fun addReferenceState(stateRef: StateRef): UtxoTransactionBuilder {
        require(stateRef !in referenceStateRefs) {
            "Duplicating reference StateRefs is not allowed."
        }
        this.referenceStateRefs += stateRef
        return this
    }

    override fun addReferenceStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        require(
            this.referenceStateRefs.intersect(stateRefs.toSet()).isEmpty() &&
                stateRefs.distinct().size == stateRefs.count()
        ) {
            "Duplicating reference StateRefs is not allowed."
        }
        this.referenceStateRefs += stateRefs
        return this
    }

    override fun addReferenceStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return addReferenceStates(stateRefs.toList())
    }

    override fun addOutputState(contractState: ContractState): UtxoTransactionBuilder {
        this.outputStates += contractState.withEncumbrance(null)
        return this
    }

    override fun addOutputStates(contractStates: Iterable<ContractState>): UtxoTransactionBuilder {
        this.outputStates += contractStates.map { it.withEncumbrance(null) }
        return this
    }

    override fun addOutputStates(vararg contractStates: ContractState): UtxoTransactionBuilder {
        return addOutputStates(contractStates.toList())
    }

    override fun addEncumberedOutputStates(
        tag: String,
        contractStates: Iterable<ContractState>
    ): UtxoTransactionBuilder {
        this.outputStates += contractStates.map { it.withEncumbrance(tag) }
        return this
    }

    override fun addEncumberedOutputStates(tag: String, vararg contractStates: ContractState): UtxoTransactionBuilder {
        return addEncumberedOutputStates(tag, contractStates.toList())
    }

    override fun getEncumbranceGroup(tag: String): List<ContractState> {
        return requireNotNull(getEncumbranceGroups()[tag]) {
            "Encumbrance group with the specified tag does not exist: $tag."
        }
    }

    override fun getEncumbranceGroups(): Map<String, List<ContractState>> {
        return outputStates
            .filter { outputState -> outputState.encumbranceTag != null }
            .groupBy { outputState -> outputState.encumbranceTag }
            .map { entry -> entry.key!! to entry.value.map { items -> items.contractState } }
            .toMap()
    }

    override fun getNotaryName(): MemberX500Name? {
        return notaryName
    }

    override fun setNotary(notaryName: MemberX500Name): UtxoTransactionBuilder {
        this.notaryName = notaryName
        this.notaryKey = lookUpNotaryKey(notaryName)

        return this
    }

    override fun getNotaryKey(): PublicKey? {
        return notaryKey
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        this.timeWindow = TimeWindowUntilImpl(until)
        return this
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
        this.timeWindow = TimeWindowBetweenImpl(from, until)
        return this
    }

    @Suspendable
    override fun toSignedTransaction(): UtxoSignedTransaction {
        return sign()
    }

    override fun copy(): UtxoTransactionBuilderContainer {
        return UtxoTransactionBuilderContainer(
            notaryName,
            timeWindow,
            commands.toMutableList(),
            signatories.toMutableList(),
            inputStateRefs.toMutableList(),
            referenceStateRefs.toMutableList(),
            outputStates.toMutableList()
        )
    }

    @Suspendable
    private fun sign(): UtxoSignedTransaction {
        check(!alreadySigned) { "The transaction cannot be signed twice." }

        UtxoTransactionBuilderVerifier(this).verify()
        return utxoSignedTransactionFactory.create(this, signatories).also {
            alreadySigned = true
        }
    }

    private fun ContractState.withEncumbrance(tag: String?): ContractStateAndEncumbranceTag {
        return ContractStateAndEncumbranceTag(this, tag)
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        return this === other ||
            other is UtxoTransactionBuilderImpl &&
            other.notaryName == notaryName &&
            other.notaryKey == notaryKey &&
            other.timeWindow == timeWindow &&
            other.commands == commands &&
            other.inputStateRefs == inputStateRefs &&
            other.referenceStateRefs == referenceStateRefs &&
            other.outputStates == outputStates &&
            other.signatories == signatories
    }

    override fun hashCode(): Int = Objects.hash(
        notaryName,
        notaryKey,
        timeWindow,
        commands,
        signatories,
        inputStateRefs,
        referenceStateRefs,
        outputStates,
    )

    override fun toString(): String {
        return "UtxoTransactionBuilderImpl(" +
            "notary=$notaryName (key: $notaryKey), " +
            "timeWindow=$timeWindow, " +
            "commands=$commands, " +
            "signatories=$signatories, " +
            "inputStateRefs=$inputStateRefs, " +
            "referenceStateRefs=$referenceStateRefs, " +
            "outputStates=$outputStates" +
            ")"
    }

    /**
     * Appends transaction builder components to a transaction builder.
     * Also, notary and time window of the original takes precedence.
     * Those will not be overwritten regardless of there are new values.
     * It de-duplicates the
     *  - signatories
     *  - inputStateRefs
     *  - referenceStateRefs
     * But keeps potential duplications in user-defined types. (commands and output states)
     */
    override fun append(other: UtxoTransactionBuilderData): UtxoTransactionBuilderImpl {
        return UtxoTransactionBuilderImpl(
            this.utxoSignedTransactionFactory,
            this.notaryLookup,
            this.notaryName ?: other.getNotaryName(),
            this.notaryKey ?: lookUpNotaryKey(other.getNotaryName()),
            this.timeWindow ?: other.timeWindow,
            (this.commands + other.commands).toMutableList(),
            (this.signatories + other.signatories).distinct().toMutableList(),
            (this.inputStateRefs + other.inputStateRefs).distinct().toMutableList(),
            (this.referenceStateRefs + other.referenceStateRefs).distinct().toMutableList(),
            (this.outputStates + other.outputStates).toMutableList()
        )
    }

    private fun lookUpNotaryKey(notaryName: MemberX500Name?): PublicKey? {
        if (notaryName == null) {
            return null
        }
        return notaryLookup.lookup(notaryName)?.publicKey
    }
}
