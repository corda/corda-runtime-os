package net.corda.flow.fiber.context

import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 The FlowFiberService provides access to the currently executing [FlowFiber]
 */
interface FlowFiberContextService : SingletonSerializeAsToken {

    /**
     * @return The executing [FlowFiber]
     * @throws [IllegalStateException] if the method is called outside a running fiber or the fiber is not an instance of [FlowFiber]
     */
    fun get(): FlowFiberExecutionContext
}