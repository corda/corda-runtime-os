package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps
import net.corda.lifecycle.Lifecycle

class LifecycleRPCOpsImpl : LifecycleRPCOps, PluggableRPCOps<LifecycleRPCOps>, Lifecycle {

    @Volatile
    private var _running = false

    override val targetInterface: Class<LifecycleRPCOps>
        get() = LifecycleRPCOps::class.java

    override val protocolVersion: Int
        get() = 2

    override fun hello(pathParam: String, param: Int?) = "Hello $param : $pathParam"

    override val isRunning: Boolean
        get() = _running

    override fun start() {
        _running = true
    }

    override fun stop() {
        _running = false
    }
}