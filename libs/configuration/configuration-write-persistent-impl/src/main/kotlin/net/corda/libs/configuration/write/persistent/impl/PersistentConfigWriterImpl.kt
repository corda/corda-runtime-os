package net.corda.libs.configuration.write.persistent.impl

import net.corda.libs.configuration.write.persistent.PersistentConfigWriter
import net.corda.messaging.api.publisher.Publisher

/** An implementation of [PersistentConfigWriter]. */
class PersistentConfigWriterImpl internal constructor(
    private val subscription: ConfigMgmtRPCSubscription,
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