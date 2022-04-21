package net.corda.flow.testing.context

import net.corda.flow.fiber.FlowIORequest

interface FlowIoRequestSetup {

    fun suspendsWith(flowIoRequest: FlowIORequest<*>)

    fun completedSuccessfullyWith(result: String?)

    fun completedWithError(exception: Exception)
}