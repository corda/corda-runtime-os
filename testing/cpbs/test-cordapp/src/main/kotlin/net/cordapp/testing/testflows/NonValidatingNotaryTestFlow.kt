package net.cordapp.testing.testflows

import com.r3.corda.notary.plugin.nonvalidating.client.NonValidatingNotaryClientFlowImpl
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.parseList
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.hours
import net.corda.v5.base.util.loggerFor
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
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
    }

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val requestMessage = requestBody.getRequestBodyAs<Map<String, String>>(jsonMarshallingService)

        val outputStateCount = requestMessage["outputStateCount"]?.toInt() ?: 0

        val inputStateRefs = requestMessage["inputStateRefs"]?.let {
            jsonMarshallingService.parseList<String>(it)
        } ?: emptyList()

        val referenceStateRefs = requestMessage["referenceStateRefs"]?.let {
            jsonMarshallingService.parseList<String>(it)
        } ?: emptyList()

        require(outputStateCount > 0 || inputStateRefs.isNotEmpty()) {
            "The transaction must have at least one input OR output state"
        }

        val timeWindowLowerBoundOffsetMs = requestMessage["timeWindowLowerBoundOffsetMs"]?.toLong()

        val timeWindowUpperBoundOffsetMs = requestMessage["timeWindowUpperBoundOffsetMs"]?.toLong()
            ?: run {
                log.info("timeWindowUpperBoundOffsetMs not provided, defaulting to 1 hour")
                1.hours.toMillis()
            }

        val notaryParty = findNotaryParty()

        val stx = buildSignedTransaction(
            notaryParty,
            inputStateRefs,
            referenceStateRefs,
            outputStateCount,
            Pair(timeWindowLowerBoundOffsetMs, timeWindowUpperBoundOffsetMs)
        )

        flowEngine.subFlow(
            NonValidatingNotaryClientFlowImpl(
                stx,
                notaryParty
            )
        )

        return jsonMarshallingService.format(NonValidatingNotaryTestFlowResult(
            stx.toLedgerTransaction().outputStateAndRefs.map { it.ref.toString() },
            stx.toLedgerTransaction().inputStateAndRefs.map { it.ref.toString() },
            stx.toLedgerTransaction().referenceInputStateAndRefs.map { it.toString() }
        ))
    }

    @Suspendable
    private fun findNotaryParty(): Party {
        // TODO CORE-6996 For now `NotaryLookup` is still work in progress, once it is finished, we
        //  need to find the notary instead of the first whose common name contains "Notary".
        val notary = memberLookup.lookup().first {
            it.name.commonName?.contains("notary", ignoreCase = true) ?: false
        }

        return Party(notary.name, notary.sessionInitiationKey)
    }

    @Suppress(
        "deprecation", // Can be removed once the new `sign` function on the TX builder is added
    )
    @Suspendable
    private fun buildSignedTransaction(
        notaryServerParty: Party,
        inputStateRefs: List<String>,
        referenceStateRefs: List<String>,
        outputStateCount: Int,
        timeWindowBounds: Pair<Long?, Long>
    ): UtxoSignedTransaction {
        return utxoLedgerService.getTransactionBuilder()
            .setNotary(notaryServerParty)
            .addCommand(TestCommand())
            .run {
                // Since the builder will always be copied with the new attributes, we always need to re-assign it
                var builder = if (timeWindowBounds.first != null) {
                    setTimeWindowBetween(
                        Instant.now().plusMillis(timeWindowBounds.first!!),
                        Instant.now().plusMillis(timeWindowBounds.second)
                    )
                } else {
                    setTimeWindowUntil(
                        Instant.now().plusMillis(timeWindowBounds.second)
                    )
                }

                repeat(outputStateCount) {
                    builder = builder.addOutputState(
                        TestContract.TestState(emptyList())
                    )
                }

                inputStateRefs.forEach {
                    builder = builder.addInputState(
                        utxoLedgerService.resolve<TestContract.TestState>(StateRef.parse(it))
                    )
                }

                referenceStateRefs.forEach {
                    builder = builder.addReferenceInputState(
                        utxoLedgerService.resolve<TestContract.TestState>(StateRef.parse(it))
                    )
                }
                builder
            }.toSignedTransaction(memberLookup.myInfo().sessionInitiationKey)
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

    data class NonValidatingNotaryTestFlowResult(
        val issuedStateRefs: List<String>,
        val consumedInputStateRefs: List<String>,
        val consumedReferenceStateRefs: List<String>
    )
}
