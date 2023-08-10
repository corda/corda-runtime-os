package com.r3.corda.testing.testflows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable
import kotlin.time.Duration.Companion.seconds

class WaitAMinuteFlow : ClientStartableFlow {
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        // Sleep for 1 minute but wake often to give flow worker a chance to stop the flow
        repeat(60) { Thread.sleep(1.seconds.inWholeMilliseconds) }
        return "Minute"
    }
}