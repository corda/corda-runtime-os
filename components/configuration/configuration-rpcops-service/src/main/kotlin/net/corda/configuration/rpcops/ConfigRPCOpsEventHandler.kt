package net.corda.configuration.rpcops

import net.corda.configuration.rpcops.v1.ConfigRPCOps
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.schema.Schemas

/** Handles incoming [LifecycleCoordinator] events for [ConfigRPCOpsService]. */
internal class ConfigRPCOpsEventHandler(
    private val publisherFactory: PublisherFactory,
    private val configRPCOps: ConfigRPCOps
) : LifecycleEventHandler {

    private companion object {
        // TODO - Joel - Describe.
        private val rpcConfig = RPCConfig(
            // TODO - Joel - Update these to send to the correct topic.
            "todo-joel-update-group-name",
            "todo-joel-update-client-name",
            Schemas.RPC.RPC_PERM_MGMT_REQ_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java
        )
    }

    /**
     * Upon [StartProcessingEvent], start handling RPC operations related to cluster configuration management.
     *
     * @throws ConfigRPCOpsServiceException TODO - Joel - Describe possible exceptions.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartProcessingEvent -> processStartProcessingEvent(event, coordinator)
            is StopEvent -> processStopEvent(coordinator)
        }
    }

    // TODO - Joel - Describe.
    private fun processStartProcessingEvent(
        event: StartProcessingEvent,
        coordinator: LifecycleCoordinator
    ) {
        println("JJJ processing start event")
        if (configRPCOps.rpcSender != null) {
            throw ConfigRPCOpsServiceException("An attempt was made to start processing twice.")
        }

        try {
            configRPCOps.rpcSender = publisherFactory.createRPCSender(rpcConfig, event.config).apply { start() }
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw ConfigRPCOpsServiceException("TODO - Joel", e)
        }

        coordinator.updateStatus(UP)
    }

    // TODO - Joel - Describe.
    private fun processStopEvent(coordinator: LifecycleCoordinator) {
        println("JJJ processing stop event")
        configRPCOps.rpcSender?.stop()
        configRPCOps.rpcSender = null
        coordinator.updateStatus(DOWN)
    }
}