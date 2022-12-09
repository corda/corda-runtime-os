package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.messaging.api.records.Record
import java.util.*

class StartTokenFlows(private val context: TaskContext) : Task {

    override fun execute() {

        val flowTargetAmounts = context.startArgs.targetAmount.ifEmpty {
            listOf(10L)
        }

        val startFlowEventRecords = flowTargetAmounts.map { getStartTokenSelectionFlow(context, it) }

        context.publish(startFlowEventRecords)
    }

    private fun getStartTokenSelectionFlow(context: TaskContext, targetAmount: Long): Record<*, *> {
        val json = """{
    "tokenType": "coin",
    "issuerHash": "${context.startArgs.shortHolderId.toSecureHashString()}",
    "notaryX500Name": "CN=Alice, O=Alice Corp, L=LDN, C=GB",
    "symbol": "${context.startArgs.tokenCcy}",
    "targetAmount": ${targetAmount},
    "tagRegex": null,
    "ownerHash": null
}"""
        return getStartRPCEventRecord(
            requestId = UUID.randomUUID().toString(),
            flowName = "net.cordapp.testing.testflows.ledger.TokenSelectionFlow",
            x500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
            groupId = "test-cordapp",
            jsonArgs = json
        )
    }
}
