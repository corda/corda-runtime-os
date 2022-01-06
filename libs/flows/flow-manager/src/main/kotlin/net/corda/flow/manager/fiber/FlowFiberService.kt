package net.corda.flow.manager.fiber

interface FlowFiberService {
    fun getExecutingFiber(): FlowFiber<*>
}