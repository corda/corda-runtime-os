package net.corda.virtualnode.rpcops.common

import net.corda.libs.configuration.SmartConfig
import java.time.Duration
import net.corda.messaging.api.publisher.config.PublisherConfig

interface VirtualNodeSenderFactory {
    fun createSender(timeout: Duration, messagingConfig: SmartConfig, asyncPublisherConfig: PublisherConfig): VirtualNodeSender
}
