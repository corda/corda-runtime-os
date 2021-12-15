package net.corda.libs.configuration.write.persistent.impl

import net.corda.libs.configuration.write.persistent.PersistentConfigWriter
import net.corda.messaging.api.publisher.Publisher
import net.corda.v5.base.util.contextLogger

/** An implementation of [PersistentConfigWriter]. */
class PersistentConfigWriterImpl internal constructor(
    private val subscription: ConfigManagementRPCSubscription,
    private val publisher: Publisher
) : PersistentConfigWriter {

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