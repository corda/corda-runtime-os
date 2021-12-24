package net.corda.permissions.storage.writer.internal

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_MGMT_REQ_TOPIC
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterServiceEventHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    private val bootstrapConfig: SmartConfig,
    private val readerService: PermissionStorageReaderService
) : LifecycleEventHandler {

    private companion object {
        const val GROUP_NAME = "user.permissions.management"
        const val CLIENT_NAME = "user.permissions.management"

        val log = contextLogger()
    }

    @VisibleForTesting
    internal var subscription: RPCSubscription<PermissionManagementRequest, PermissionManagementResponse>? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.info("Start event received")
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Status Change Event received: $event")
                when (event.status) {
                    LifecycleStatus.UP -> {
                        val subscription = subscriptionFactory.createRPCSubscription(
                            rpcConfig = RPCConfig(
                                groupName = GROUP_NAME,
                                clientName = CLIENT_NAME,
                                requestTopic = RPC_PERM_MGMT_REQ_TOPIC,
                                requestType = PermissionManagementRequest::class.java,
                                responseType = PermissionManagementResponse::class.java
                            ),
                            nodeConfig = bootstrapConfig,
                            responderProcessor = permissionStorageWriterProcessorFactory.create(
                                entityManagerFactory,
                                readerService.permissionStorageReader!!
                            )
                        ).also {
                            this.subscription = it
                        }
                        subscription.start()
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        downTransition(coordinator)
                    }
                    LifecycleStatus.ERROR -> {
                        coordinator.updateStatus(LifecycleStatus.ERROR)
                        coordinator.stop()
                    }
                }
            }
            is StopEvent -> {
                log.info("Stop event received")
                downTransition(coordinator)
            }
        }
    }

    private fun downTransition(coordinator: LifecycleCoordinator) {
        subscription?.stop()
        subscription = null
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }
}