package net.corda.flow.manager.fiber

/**
 The FlowFiberService provides access to the currently executing [FlowFiber]
 */
interface FlowFiberService {

    /**
     * @return The executing [FlowFiber]
     * @throws [IllegalStateException] if the method is called outside a running fiber or the fiber is not an instance of [FlowFiber]
     */
    fun getExecutingFiber(): FlowFiber<*>
}