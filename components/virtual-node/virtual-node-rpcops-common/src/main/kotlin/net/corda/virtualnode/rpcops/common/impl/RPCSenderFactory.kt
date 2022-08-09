package net.corda.virtualnode.rpcops.common.impl

import net.corda.libs.configuration.SmartConfig
import java.time.Duration

interface RPCSenderFactory {
    fun createSender(timeout: Duration, messagingConfig: SmartConfig): RPCSenderWrapperImpl
}