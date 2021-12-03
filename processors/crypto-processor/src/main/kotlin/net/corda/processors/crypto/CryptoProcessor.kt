package net.corda.processors.crypto

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `CryptoWorker`. */
@Component(service = [CryptoProcessor::class])
class CryptoProcessor {
    private companion object {
        val logger = contextLogger()
    }

    var config: SmartConfig? = null

    fun start() {
        logger.info("Crypto processor starting.")
    }

    fun stop() {
        logger.info("Crypto processor stopping.")
    }
}