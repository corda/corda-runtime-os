package net.corda.tools.bundle.cleanup

import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true)
@Suppress("unused")
class Main @Activate constructor(
    @Reference(service = DemoPublisher::class)
    private val publisher: DemoPublisher,
    @Reference(service = DemoConsumer::class)
private val consumer: DemoConsumer
) : Application {
    companion object {
        private val log = contextLogger()
    }

    override fun startup(args: Array<String>) {
        log.info("JJJ - Main started.")
        consumer.start()
        publisher.start()
    }

    override fun shutdown() = Unit
}