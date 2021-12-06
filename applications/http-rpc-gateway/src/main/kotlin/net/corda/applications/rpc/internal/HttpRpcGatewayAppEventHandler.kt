package net.corda.applications.rpc.internal

import net.corda.components.rpc.HttpRpcGateway
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HttpRpcGatewayAppEventHandler(
    private val httpRpcGateway: HttpRpcGateway
) : LifecycleEventHandler {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("LifecycleEvent received: $event")
        when (event) {
            is StartEvent -> {
                consoleLogger.info("Following gateway and config read service for status updates.")
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<HttpRpcGateway>()
                        )
                    )

                consoleLogger.info("Starting HTTP RPC Gateway component.")
                httpRpcGateway.start()
                consoleLogger.info("HTTP RPC Gateway application started")
            }
            is RegistrationStatusChangeEvent -> {
                consoleLogger.info("HTTP RPC Gateway component reported ${event.status}.")
                coordinator.updateStatus(event.status)

                if(event.status == LifecycleStatus.ERROR) {
                    coordinator.stop()
                }
            }
            is StopEvent -> {
                consoleLogger.info("Stopping HTTP RPC Gateway")
                httpRpcGateway.stop()
                registration?.close()
                registration = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}