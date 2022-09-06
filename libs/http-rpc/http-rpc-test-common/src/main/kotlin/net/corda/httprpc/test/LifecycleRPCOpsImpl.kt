package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps
import net.corda.lifecycle.Resource

class LifecycleRPCOpsImpl : LifecycleRPCOps, PluggableRPCOps<LifecycleRPCOps>, Resource {

    @Volatile
    private var _running = false

    override val targetInterface: Class<LifecycleRPCOps>
        get() = LifecycleRPCOps::class.java

    override val protocolVersion: Int
        get() = 2

    override fun hello(pathParam: String, param: Int?) : String {
        return "Hello $param : $pathParam"
    }

    fun start() {
        _running = true
    }

    override fun close() {
        _running = false
    }
}
