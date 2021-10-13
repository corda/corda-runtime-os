package net.corda.tools.bundle.cleanup

import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import kotlin.concurrent.thread

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
        log.info("JJJ - Starting the consumer and publisher.")
        publishAndConsumeRecords()

        log.info("JJJ - Starting the consumer and publisher again.")
        publishAndConsumeRecords()
    }

    override fun shutdown() = Unit

    private fun publishAndConsumeRecords() {
        consumer.start()

        thread {
            repeat((0..10).count()) {
                publisher.publish("z", "1")
                Thread.sleep(100)
            }
        }

        Thread.sleep(1000)
        consumer.stop()
    }
}