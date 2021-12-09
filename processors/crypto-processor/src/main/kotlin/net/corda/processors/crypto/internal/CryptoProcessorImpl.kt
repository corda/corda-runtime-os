package net.corda.processors.crypto.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** An implementation of [CryptoProcessor]. */
@Suppress("Unused")
@Component(service = [CryptoProcessor::class])
class CryptoProcessorImpl: CryptoProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(instanceId: Int, topicPrefix: String, config: SmartConfig) {
        logger.info("Crypto processor starting.")
    }

    override fun stop() {
        logger.info("Crypto processor stopping.")
    }
}