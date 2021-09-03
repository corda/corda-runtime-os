package net.corda.sample.goodbye

import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component(service = [Application::class])
class GoodbyeWorld : Application {

    private companion object {
        private val log : Logger = contextLogger()
    }

    init {
        log.info("Constructing ${javaClass.name}")
    }

    override fun run(args: Array<String>) : Int {
        log.info("START-UP")
        Thread {
            Thread.sleep(1000)
        }.apply {
            start()
            join()
        }
        return 0
    }
}

