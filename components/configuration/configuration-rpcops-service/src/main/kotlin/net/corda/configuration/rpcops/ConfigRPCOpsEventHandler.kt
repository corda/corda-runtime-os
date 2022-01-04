package net.corda.configuration.rpcops

import net.corda.config.schema.Schema
import net.corda.config.schema.Schema.Companion.CONFIG_MGMT_REQUEST_TOPIC
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
    private val configRPCOpsRpcSender: ConfigRPCOpsRPCSender
) : LifecycleEventHandler {

    private companion object {
        // TODO - Joel - Describe.
        private val rpcConfig = RPCConfig(
            // TODO - Joel - Update these to send to the correct topic.
            "config.management",
            "config.manager.client",
            CONFIG_MGMT_REQUEST_TOPIC,
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
    private fun processStartProcessingEvent(event: StartProcessingEvent, coordinator: LifecycleCoordinator) {
        if (configRPCOpsRpcSender.rpcSender != null) {
            throw ConfigRPCOpsServiceException("An attempt was made to start processing twice.")
        }

        try {
            // TODO - Joel - Encapsulate this in a method on configRPCOpsRpcSender.
            configRPCOpsRpcSender.rpcSender = publisherFactory.createRPCSender(rpcConfig, event.config).apply { start() }
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw ConfigRPCOpsServiceException("TODO - Joel", e)
        }

        coordinator.updateStatus(UP)
    }

    // TODO - Joel - Describe.
    private fun processStopEvent(coordinator: LifecycleCoordinator) {
        // TODO - Joel - Encapsulate this in a method on configRPCOpsRpcSender.
        configRPCOpsRpcSender.rpcSender?.stop()
        configRPCOpsRpcSender.rpcSender = null
        coordinator.updateStatus(DOWN)
    }
}