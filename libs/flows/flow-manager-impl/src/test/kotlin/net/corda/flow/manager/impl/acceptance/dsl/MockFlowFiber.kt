package net.corda.flow.manager.impl.acceptance.dsl

import net.corda.flow.manager.fiber.FlowIORequest
import java.nio.ByteBuffer
import java.util.UUID

class MockFlowFiber(val flowId: String = UUID.randomUUID().toString()) {

    private var requests = mutableListOf<FlowIORequest<*>>()

    fun queueSuspension(request: FlowIORequest<*>) {
        requests.add(
            when (request) {
                is FlowIORequest.FlowFinished -> request
                is FlowIORequest.FlowFailed -> request
                else -> FlowIORequest.FlowSuspended(ByteBuffer.wrap(byteArrayOf(0)), request)
            }
        )
    }

    fun repeatSuspension(request: FlowIORequest<*>, times: Int) {
        repeat(times) { queueSuspension(request) }
    }

    fun dequeueSuspension(): FlowIORequest<*> {
        check(requests.isNotEmpty()) { "The next ${FlowIORequest::class.java.simpleName} that the mocked flow returns must be set" }
        return requests.removeFirst()
    }
}