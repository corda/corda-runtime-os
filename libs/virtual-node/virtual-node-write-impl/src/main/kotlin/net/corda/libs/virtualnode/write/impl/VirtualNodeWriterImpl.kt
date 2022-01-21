package net.corda.libs.virtualnode.write.impl

import net.corda.libs.virtualnode.write.VirtualNodeWriter
import net.corda.messaging.api.publisher.Publisher

/** An implementation of [VirtualNodeWriter]. */
internal class VirtualNodeWriterImpl internal constructor(
    private val subscription: ConfigurationManagementRPCSubscription,
    private val publisher: Publisher
) : VirtualNodeWriter {

    override val isRunning get() = subscription.isRunning

    override fun start() {
        subscription.start()
        publisher.start()
    }

    override fun stop() {
        subscription.stop()
        publisher.close()
    }
}