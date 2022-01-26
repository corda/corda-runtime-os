package net.corda.libs.virtualnode.write.impl

import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.messaging.api.subscription.RPCSubscription
import java.util.concurrent.CompletableFuture

internal typealias VirtualNodeCreationResponseFuture = CompletableFuture<VirtualNodeCreationResponse>
internal typealias VirtualNodeCreationRPCSubscription =
        RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse>

internal const val GROUP_NAME = "virtual.node.management"
internal const val CLIENT_NAME_DB = "virtual.node.manager.db"
internal const val CLIENT_NAME_RPC = "virtual.node.manager.rpc"