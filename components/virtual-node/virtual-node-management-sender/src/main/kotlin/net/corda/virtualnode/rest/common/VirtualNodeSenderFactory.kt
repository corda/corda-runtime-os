package net.corda.virtualnode.rest.common

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import java.time.Duration

interface VirtualNodeSenderFactory {
    fun createSender(
        timeout: Duration, messagingConfig: SmartConfig, asyncPublisherConfig: PublisherConfig
    ): VirtualNodeSender
}
