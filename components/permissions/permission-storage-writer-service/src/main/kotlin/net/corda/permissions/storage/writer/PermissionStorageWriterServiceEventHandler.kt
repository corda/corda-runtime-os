package net.corda.permissions.storage.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_MGMT_REQ_TOPIC
import net.corda.v5.base.annotations.VisibleForTesting
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterServiceEventHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    private val nodeConfig: SmartConfig
) : LifecycleEventHandler {

    private companion object {
        const val GROUP_NAME = "user.permissions.management"
        const val CLIENT_NAME = "user.permissions.management"
    }

    @VisibleForTesting
    internal var subscription: RPCSubscription<PermissionManagementRequest, PermissionManagementResponse>? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                val subscription = subscriptionFactory.createRPCSubscription(
                    rpcConfig = RPCConfig(
                        groupName = GROUP_NAME,
                        clientName = CLIENT_NAME,
                        requestTopic = RPC_PERM_MGMT_REQ_TOPIC,
                        requestType = PermissionManagementRequest::class.java,
                        responseType = PermissionManagementResponse::class.java
                    ),
                    nodeConfig = nodeConfig,
                    responderProcessor = permissionStorageWriterProcessorFactory.create(entityManagerFactory)
                ).also {
                    this.subscription = it
                }
                subscription.start()
            }
            is StopEvent -> {
                subscription?.stop()
                subscription = null
            }
        }
    }
}