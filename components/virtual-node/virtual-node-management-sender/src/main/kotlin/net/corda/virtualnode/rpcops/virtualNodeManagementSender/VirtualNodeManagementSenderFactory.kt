package net.corda.virtualnode.rpcops.virtualNodeManagementSender

import net.corda.libs.configuration.SmartConfig
import java.time.Duration

interface VirtualNodeManagementSenderFactory {
    fun createSender(timeout: Duration, messagingConfig: SmartConfig): VirtualNodeManagementSender
}
