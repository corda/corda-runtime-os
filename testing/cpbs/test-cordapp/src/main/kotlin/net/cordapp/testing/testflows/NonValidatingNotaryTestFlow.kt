package net.cordapp.testing.testflows

import com.r3.corda.notary.plugin.nonvalidating.client.NonValidatingNotaryClientFlowImpl
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.hours
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey
import java.time.Instant

/**
 * This flow is used to call the `NonValidatingNotaryClientFlowImpl`. Since `NonValidatingNotaryClientFlowImpl` is not
 * a HTTP RPC invokable flow, we need an extra layer to call that flow from tests and through HTTP RPC.
 *
 * This flow will generate a UTXO signed transaction using the provided HTTP parameters and pass that signed transaction
 * to the non-validating notary plugin. This flow will automatically find a notary on the network and use that as the
 * primary notary.
 *
 * This flow will take in the following parameters through HTTP:
 * - `timeWindowLowerBoundOffsetMs`: The lower bound offset for the generated transaction's time window. This can either
 * be positive or negative. As an example: If `-10000` is provided, the transaction's time window will start from current
 * UTC time ([Instant.now]) - 10 seconds. If not provided, no lower bound is assumed.
 *
 * - `timeWindowUpperBoundOffsetMs`: The upper bound offset for the generated transaction's time window. This can either
 * be positive or negative. As an example: If `3600000` is provided, the transaction's time window will end at UTC time
 * ([Instant.now]) + 1 hour. If not provided, it will default to 1 hour, which means that the time window's upper bound
 * will be the current  UTC time ([Instant.now]) + 1 hour.
 *
 * TODO CORE-7939 Add extra state parameters once they are available.
 * TODO CORE-7939 This test flow should be modified once the Ledger (UtxoLedgerTransactionImpl and back-chain resolution)
 *  has been finished. Since `inputStateAndRefs` returns an empty list for now, the uniqueness check will always
 *  pass as each transaction will be considered an ISSUANCE transaction. After the ledger is in a better state
 *  we'll need to ISSUE states first before we can CONSUME them.
 */
@InitiatingFlow(protocol = "non-validating-test")
class NonValidatingNotaryTestFlow : RPCStartableFlow {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    private companion object {
        val log = loggerFor<NonValidatingNotaryTestFlow>()

        // TODO CORE-7939 Can be removed after ledger has been fixed
        const val DUMMY_TX_ID = "SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903"
        const val DUMMY_TX_ID_2 = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
    }

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val requestMessage = requestBody.getRequestBodyAs<Map<String, String>>(jsonMarshallingService)

        val timeWindowLowerBoundOffsetMs = requestMessage["timeWindowLowerBoundOffsetMs"]?.toLong()

        val timeWindowUpperBoundOffsetMs = requestMessage["timeWindowUpperBoundOffsetMs"]?.toLong()
            ?: run {
                log.info("timeWindowUpperBoundOffsetMs not provided, defaulting to 1 hour")
                1.hours.toMillis()
            }

        val myInfo = memberLookup.myInfo()

        // TODO CORE-6996 For now `NotaryLookup` is still work in progress, once it is finished, we need to find the
        //  notary instead of a random member
        val notary = memberLookup.lookup().first {
            it.name != myInfo.name
        }

        val notaryParty = Party(notary.name, notary.sessionInitiationKey)

        val stx = utxoLedgerService.getTransactionBuilder()
            .setNotary(notaryParty)
            .addCommand(TestCommand())
            // TODO CORE-7939 Can be removed after empty component groups have been fixed
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            // TODO CORE-7939 For now we are spending non-existent states this needs rework after ledger
            //  work is finished
            .addInputState(generateStateAndRef(DUMMY_TX_ID, 0, notaryParty))
            .addReferenceInputState(generateStateAndRef(DUMMY_TX_ID_2, 0, notaryParty))
            .addOutputState(TestContract.TestState(emptyList()))
            .run {
                if (timeWindowLowerBoundOffsetMs != null) {
                    setTimeWindowBetween(
                        Instant.now().plusMillis(timeWindowLowerBoundOffsetMs),
                        Instant.now().plusMillis(timeWindowUpperBoundOffsetMs)
                    )
                } else {
                    setTimeWindowUntil(
                        Instant.now().plusMillis(timeWindowUpperBoundOffsetMs)
                    )
                }
            }
            .sign(myInfo.sessionInitiationKey)

        val sigs = flowEngine.subFlow(
            NonValidatingNotaryClientFlowImpl(
                stx,
                notaryParty
            )
        )

        return "Received ${sigs.size} signatures from the notary, plugin ran successfully."
    }

    /**
     * The contract and command classes are needed to build a signed UTXO transaction. Unfortunately, we cannot reuse
     * any internal contract/command as this flow belongs to an "external" CorDapp (CPB) so we can't introduce internal
     * dependencies.
     */
    class TestContract : Contract {
        class TestState(override val participants: List<PublicKey>) : ContractState

        override fun verify(transaction: UtxoLedgerTransaction) {}
    }
    class TestCommand : Command

    // TODO CORE-7939 These must be removed once the transaction builder logic has been fixed
    //  They are only needed because empty component groups are not supported, thus we cannot create issuance
    //  transactions and we can't access the actual `TransactionStateImpl` and `StateAndRefImpl` classes.
    class TestTxStateImpl(
        override val contractState: TestContract.TestState,
        override val contractStateType: Class<out TestContract.TestState>,
        override val contractType: Class<out Contract>,
        override val encumbrance: Int?,
        override val notary: Party
    ) : TransactionState<TestContract.TestState>

    class TestStateAndRefImpl(
        override val ref: StateRef,
        override val state: TransactionState<TestContract.TestState>
    ) : StateAndRef<TestContract.TestState>

    private fun generateStateAndRef(txId: String, index: Int, notary: Party) : StateAndRef<TestContract.TestState> {
        val txHash = SecureHash.parse(txId)
        val stateRef = StateRef(txHash, index)

        val transactionState = TestTxStateImpl(
            TestContract.TestState(emptyList()),
            TestContract.TestState::class.java,
            TestContract::class.java,
            null,
            notary
        )
        return TestStateAndRefImpl(
            stateRef,
            transactionState
        )
    }
}
