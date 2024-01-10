package net.corda.rest.test

import net.corda.rest.PluggableRestResource
import net.corda.lifecycle.Lifecycle

class LifecycleRestResourceImpl : LifecycleRestResource, PluggableRestResource<LifecycleRestResource>, Lifecycle {

    @Volatile
    private var _running = false

    override val targetInterface: Class<LifecycleRestResource>
        get() = LifecycleRestResource::class.java

    override val protocolVersion: Int
        get() = 2

    override fun hello(pathParam: String, param: Int?) : String {
        return "Hello $param : $pathParam"
    }

    override val isRunning: Boolean
        get() = _running

    override fun start() {
        _running = true
    }

    override fun stop() {
        _running = false
    }
}
