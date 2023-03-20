package net.cordapp.testing.testflows

import com.r3.corda.notary.plugin.nonvalidating.client.NonValidatingNotaryClientFlowImpl
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.cordapp.demo.utxo.contract.TestCommand
import net.cordapp.demo.utxo.contract.TestUtxoState
import org.slf4j.LoggerFactory
import java.time.Duration
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
class NonValidatingNotaryTestFlow : ClientStartableFlow {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var digestService: DigestService

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val params = extractParameters(requestBody)

        require(params.outputStateCount > 0 || params.inputStateRefs.isNotEmpty()) {
            "The transaction must have at least one input OR output state"
        }

        val isIssuance = params.inputStateRefs.isEmpty()

        val notaryServiceParty = findNotaryServiceParty()

        val stx = buildSignedTransaction(
            notaryServiceParty,
            params.outputStateCount,
            params.inputStateRefs,
            params.referenceStateRefs,
            Pair(params.timeWindowLowerBoundOffsetMs, params.timeWindowUpperBoundOffsetMs)
        )

        if (isIssuance) {
            // If we have an issuance it means we need to run it through finality flow, to make sure the issued state
            // is saved to the vault so we can spend later on
            utxoLedgerService.finalize(stx, emptyList())
        } else {
            // If we have a consume it means we can send it directly to the plugin, because we already have a valid state
            // that we can spend
            val signatures = flowEngine.subFlow(NonValidatingNotaryClientFlowImpl(
                stx,
                findNotaryVNodeParty()
            ))

            // Since we are not calling finality flow for consuming transactions we need to verify that the signature
            // is actually part of the notary service's composite key
            signatures.forEach {
                require(KeyUtils.isKeyInSet(notaryServiceParty.owningKey, listOf(it.by))) {
                    "The plugin responded with a signature that is not part of the notary service's composite key."
                }
            }
        }

        return jsonMarshallingService.format(NonValidatingNotaryTestFlowResult(
            stx.outputStateAndRefs.map { it.ref.toString() },
            stx.inputStateRefs.map { it.toString() },
            stx.referenceStateRefs.map { it.toString() }
        ))
    }

    /**
     * A helper function that extracts the required parameters from the JSON request body into a
     * [NotarisationTestFlowParameters] object so it is easily accessible and this way the parsing
     * logic is separated from the main flow logic in [call].
     */
    @Suppress("CyclomaticComplexMethod")
    @Suspendable
    private fun extractParameters(requestBody: ClientRequestBody): NotarisationTestFlowParameters {
        val requestMessage = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val outputStateCount = requestMessage["outputStateCount"]?.toInt() ?: 0

        val inputStateRefs = requestMessage["inputStateRefs"]?.let {
            jsonMarshallingService.parseList(it, String::class.java)
        } ?: emptyList()

        val referenceStateRefs = requestMessage["referenceStateRefs"]?.let {
            jsonMarshallingService.parseList(it, String::class.java)
        } ?: emptyList()

        val timeWindowLowerBoundOffsetMs = requestMessage["timeWindowLowerBoundOffsetMs"]?.toLong()

        val timeWindowUpperBoundOffsetMs = requestMessage["timeWindowUpperBoundOffsetMs"]?.toLong()
            ?: run {
                log.info("timeWindowUpperBoundOffsetMs not provided, defaulting to 1 hour")
                Duration.ofHours(1).toMillis()
            }

        return NotarisationTestFlowParameters(
            outputStateCount,
            inputStateRefs,
            referenceStateRefs,
            timeWindowLowerBoundOffsetMs,
            timeWindowUpperBoundOffsetMs
        )
    }

    /**
     * A helper function that will find the notary service party on the network. This is attached to the transaction
     * and will be used by the finality flow to do the VNode selection itself.
     */
    @Suspendable
    private fun findNotaryServiceParty(): Party {
        val notary = notaryLookup.notaryServices.single()
        return Party(notary.name, notary.publicKey)
    }

    /**
     * A helper function that will find the notary VNode party on the network. This basically acts as a VNode selection
     * logic like the one we have in the finality flow. When we call the plugin directly we must select a VNode
     * beforehand as the plugin has no logic for VNode selection.
     */
    private fun findNotaryVNodeParty(): Party {
        // We cannot use the notary virtual node lookup service in this flow so we need to do this hack
        val notary = memberLookup.lookup().first {
            it.name.commonName?.contains("notary", ignoreCase = true) ?: false
        }

        return Party(notary.name, notary.sessionInitiationKey)
    }

    /**
     * A helper function that will build a UTXO signed transaction from the provided input parameters using the
     * [UtxoTransactionBuilder] utility class.
     */
    @Suspendable
    private fun buildSignedTransaction(
        notaryServerParty: Party,
        outputStateCount: Int,
        inputStateRefs: List<String>,
        referenceStateRefs: List<String>,
        timeWindowBounds: Pair<Long?, Long>
    ): UtxoSignedTransaction {
        val myKey = memberLookup.myInfo().sessionInitiationKey
        return utxoLedgerService.getTransactionBuilder()
                .setNotary(notaryServerParty)
                .addCommand(TestCommand())
                .run {
                    // TODO CORE-8726 Since the builder will always be copied with the new attributes,
                    //  we always need to re-assign it
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
                            TestUtxoState("test", emptyList(), emptyList())
                        )
                    }

                    inputStateRefs.forEach {
                        builder = builder.addInputState(StateRef.parse(it, digestService))
                    }

                    referenceStateRefs.forEach {
                        builder = builder.addReferenceState(StateRef.parse(it, digestService))
                    }
                    builder = builder.addSignatories(listOf(myKey))
                    builder
                }.toSignedTransaction()
    }

    /**
     * A basic data class that represents the outcome of the [NonValidatingNotaryTestFlow] flow.
     */
    data class NonValidatingNotaryTestFlowResult(
        val issuedStateRefs: List<String>,
        val consumedInputStateRefs: List<String>,
        val consumedReferenceStateRefs: List<String>
    )

    /**
     * A basic data class that represents the required parameters for the [NonValidatingNotaryTestFlow] flow.
     */
    data class NotarisationTestFlowParameters(
        val outputStateCount: Int,
        val inputStateRefs: List<String>,
        val referenceStateRefs: List<String>,
        val timeWindowLowerBoundOffsetMs: Long?,
        val timeWindowUpperBoundOffsetMs: Long
    )
}
