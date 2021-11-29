package net.corda.processors.flow

import com.typesafe.config.Config
import net.corda.v5.base.util.contextLogger

// TODO - Joel - Describe.
class FlowProcessor {
    private companion object {
        val logger = contextLogger()
    }

    fun startup(config: Config) {
        logger.info(config.toString())
    }
}