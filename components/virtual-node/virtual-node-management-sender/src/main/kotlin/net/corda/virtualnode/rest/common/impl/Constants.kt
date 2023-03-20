package net.corda.virtualnode.rest.common.impl

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas

private const val GROUP_NAME = "virtual.node.management"
private const val CLIENT_NAME_HTTP = "virtual.node.manager.http"
val SENDER_CONFIG = RPCConfig(
    GROUP_NAME,
    CLIENT_NAME_HTTP,
    Schemas.VirtualNode.VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
    VirtualNodeManagementRequest::class.java,
    VirtualNodeManagementResponse::class.java
)
