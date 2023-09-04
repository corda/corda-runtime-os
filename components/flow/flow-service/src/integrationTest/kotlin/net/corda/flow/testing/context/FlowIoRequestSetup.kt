package net.corda.flow.testing.context

import net.corda.flow.fiber.FlowIORequest

interface FlowIoRequestSetup {

    fun suspendsWith(flowIoRequest: FlowIORequest<*>) : FlowIoRequestSetup

    fun completedSuccessfullyWith(result: String?) : FlowIoRequestSetup

    fun completedWithError(exception: Exception) : FlowIoRequestSetup
}