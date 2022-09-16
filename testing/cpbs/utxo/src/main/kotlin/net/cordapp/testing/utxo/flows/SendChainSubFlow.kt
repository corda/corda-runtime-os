package net.cordapp.testing.utxo.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name
import net.cordapp.testing.utxo.Block
import net.cordapp.testing.utxo.messages.Transaction


@InitiatingFlow(protocol = "sendcoins")
class SendChainSubFlow(
    val member: MemberX500Name,
    val inputBlocksToNewTransaction: Set<Block>,
    val backChainBlocks: Set<Block>,
    val amount: Long
) : SubFlow<Unit> {
    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    override fun call() {
        val session = flowMessaging.initiateFlow(member)
        session.send(
            Transaction(
                inputBlocksToNewTransaction = inputBlocksToNewTransaction,
                backChainBlocks = backChainBlocks,
                amount
            )
        )

        // @@@ here we should ideally wait for a response from the other party to let us know they validated
        // successfully, otherwise we risk losing the coins
    }
}
