package net.corda.libs.configuration.write.impl

import net.corda.libs.configuration.write.ConfigWriter
import net.corda.messaging.api.publisher.Publisher

/** An implementation of [ConfigWriter]. */
internal class ConfigWriterImpl internal constructor(
    private val subscription: ConfigMgmtRPCSubscription,
    private val publisher: Publisher
) : ConfigWriter {

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