package net.corda.flow.manager.factory

import net.corda.flow.manager.FlowManager
import net.corda.sandbox.cache.SandboxCache

interface FlowManagerFactory {

    fun createFlowManager(sandboxCache: SandboxCache): FlowManager
}