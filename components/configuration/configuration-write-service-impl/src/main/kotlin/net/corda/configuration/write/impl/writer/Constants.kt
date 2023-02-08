package net.corda.configuration.write.impl.writer

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.messaging.api.subscription.RPCSubscription
import java.util.concurrent.CompletableFuture

internal typealias ConfigurationManagementResponseFuture = CompletableFuture<ConfigurationManagementResponse>
internal typealias ConfigurationManagementRPCSubscription =
        RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>

internal const val GROUP_NAME = "config.management"
internal const val CLIENT_NAME_DB = "config.manager.db"
internal const val CLIENT_NAME_RPC = "config.manager.rest"