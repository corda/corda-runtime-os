package com.r3.corda.notary.plugin.contractverifying.client

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import com.r3.corda.notary.plugin.contractverifying.api.FilteredTransactionAndSignatures
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "com.r3.corda.notary.plugin.contractverifying", version = [1])
class ContractVerifyingNotaryClientFlowImpl(
    private val stx: UtxoSignedTransaction,
    private val notaryRepresentative: MemberX500Name
) : PluggableNotaryClientFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var flowMessaging: FlowMessaging

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    private lateinit var digestService: DigestService

    // TODO Dummy logic for now
    @Suspendable
    override fun call(): List<DigitalSignatureAndMetadata> {
        log.info("Calling Contract Verifying Notary client...")

        val session = flowMessaging.initiateFlow(notaryRepresentative)

        val payload = generatePayload()

        val notarizationResponse = session.sendAndReceive(
            NotarizationResponse::class.java,
            payload
        )

        return notarizationResponse.signatures
    }

    @Suspendable
    internal fun generatePayload(): ContractVerifyingNotarizationPayload {

        val hashedNotaryKey = digestService.hash(stx.notaryKey.encoded, DigestAlgorithmName.SHA2_256)

        // TODO This part could be optimised if we could change the plugin client's constructor and just pass in the
        //  already filtered stuff from the finality flow
        val filteredDependenciesAndSignatures = stx.let { it.inputStateRefs + it.referenceStateRefs }
            .groupBy { stateRef -> stateRef.transactionId }
            .mapValues { (_, stateRefs) -> stateRefs.map { stateRef -> stateRef.index } }
            .map { (transactionId, indexes) ->
                val dependency = requireNotNull(utxoLedgerService.findSignedTransaction(transactionId)) {
                    "Dependant transaction $transactionId does not exist"
                }
                FilteredTransactionAndSignatures(
                    utxoLedgerService.filterSignedTransaction(dependency)
                        .withOutputStates(indexes)
                        .withNotary()
                        .withTimeWindow()
                        .build(),
                    dependency.signatures.filter { hashedNotaryKey == it.by }
                )
            }
        return ContractVerifyingNotarizationPayload(stx, filteredDependenciesAndSignatures, stx.notaryKey)
    }
}
