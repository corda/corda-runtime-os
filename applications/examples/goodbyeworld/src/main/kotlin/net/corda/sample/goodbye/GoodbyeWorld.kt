package net.corda.sample.goodbye

import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component(immediate = true)
class GoodbyeWorld @Activate constructor() : Application {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    init {
        logger.info("INIT")
    }

    fun activate() {
        logger.info("START")
    }

    override fun run(args: Array<String>) : Int {
        logger.info("START-UP")
        Thread.sleep(1000)
        return 0
    }

    fun deactivate() {
        logger.info("STOP")
    }
}

