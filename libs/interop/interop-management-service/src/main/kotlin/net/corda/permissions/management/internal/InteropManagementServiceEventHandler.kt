package net.corda.libs.interop.endpoints.v1

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler

import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class InteropManagementServiceEventHandler : LifecycleEventHandler {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
//        const val GROUP_NAME = "rpc.interop.management"
//        const val CLIENT_NAME = "rpc.interop.manager"
    }

    @Volatile
    internal var interopManager: InteropManager? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        TODO("implement")
    }

}
