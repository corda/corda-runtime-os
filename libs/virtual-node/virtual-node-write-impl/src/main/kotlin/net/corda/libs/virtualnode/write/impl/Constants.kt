package net.corda.libs.virtualnode.write.impl

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.messaging.api.subscription.RPCSubscription
import java.util.concurrent.CompletableFuture

internal typealias ConfigurationManagementResponseFuture = CompletableFuture<ConfigurationManagementResponse>
internal typealias ConfigurationManagementRPCSubscription =
        RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>

internal const val GROUP_NAME = "virtual.node.management"
internal const val CLIENT_NAME_DB = "virtual.node.manager.db"
internal const val CLIENT_NAME_RPC = "virtual.node.manager.rpc"