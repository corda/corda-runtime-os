package net.corda.p2p.app.topic.dump

import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine

@Component(immediate = true)
internal class Application @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : Application {
    private val topicDumper = TopicDumper(subscriptionFactory)
    override fun startup(args: Array<String>) {
        val command = CommandLine(topicDumper)
        @Suppress("SpreadOperator")
        if (command.execute(*args) != 0) {
            throw TopicDumperException("Could not execute")
        }
        if (command.isUsageHelpRequested) {
            shutDownService.shutdown(FrameworkUtil.getBundle(this.javaClass))
        }
    }

    override fun shutdown() {
        topicDumper.close()
    }

    class TopicDumperException(message: String) : Exception(message)
}
