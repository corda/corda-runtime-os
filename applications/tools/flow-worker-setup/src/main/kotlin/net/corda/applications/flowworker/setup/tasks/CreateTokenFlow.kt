package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import java.util.*

class CreateTokenFlow(private val context: TaskContext) : Task {

    override fun execute() {
        val json = """{
    "tokenType": "coin",
    "issuerHash": "${context.startArgs.shortHolderId.toSecureHashString()}",
    "notaryX500Name": "CN=Alice, O=Alice Corp, L=LDN, C=GB",
    "symbol": "GBP",
    "targetAmount": 100,
    "tagRegex": 'T1 T2',
    "ownerHash": null
}"""

        val startRecord = getStartRPCEventRecord(
            requestId = UUID.randomUUID().toString(),
            flowName = "net.cordapp.testing.testflows.CreateTokenFlow",
            x500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
            groupId = "test-cordapp",
            jsonArgs = json
        )

        context.publish(startRecord)
    }
}