package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.permissions.storage.writer.PermissionStorageWriter
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_MGMT_REQ_TOPIC
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterImpl(
    private val subscriptionFactory: SubscriptionFactory,
    private val entityManagerFactory: EntityManagerFactory
) : PermissionStorageWriter {

    private companion object {
        const val GROUP_NAME = "user.permissions.management"
        const val CLIENT_NAME = "user.permissions.management"
    }

    override val isRunning: Boolean get() = !stopped

    private var stopped = false

    private var subscription: RPCSubscription<PermissionManagementRequest, Unit>? = null

    override fun start() {
        stopped = false
        processIncomingManagementRequests()
    }

    override fun stop() {
        subscription?.close()
        stopped = true
    }

    private fun processIncomingManagementRequests() {
        subscription = subscriptionFactory.createRPCSubscription(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = RPC_PERM_MGMT_REQ_TOPIC,
                requestType = PermissionManagementRequest::class.java,
                responseType = Unit::class.java
            ),
            nodeConfig = SmartConfigImpl.empty(),
            responderProcessor = PermissionStorageWriterProcessor(entityManagerFactory)
        )
    }
}