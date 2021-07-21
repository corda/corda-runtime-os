package net.corda.components.examples.runflow

import com.typesafe.config.Config
import net.corda.components.examples.runflow.processor.DemoRunFlowProcessor
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.FlowManager
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunFlow(
    private val flowManager: FlowManager,
    private val subscriptionFactory: SubscriptionFactory,
    private var config: Config
) : LifeCycle {

    companion object {
        val log: Logger = contextLogger()
        const val groupName = "pubsubGroup"
        const val pubsubTopic = "PubsubTopic"
    }

    private var subscription: StateAndEventSubscription<String, Checkpoint, FlowEvent>? = null

    override var isRunning: Boolean = false

    override fun start() {
        if (!isRunning) {
            log.info("Creating flow runner")
            isRunning = true
            val processor = DemoRunFlowProcessor(flowManager)
            subscription = subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig(groupName, pubsubTopic),
                processor,
                config
            )

            log.info("Starting flow runner")
            subscription?.start()
        }
    }

    override fun stop() {
        log.info("Stopping flow runner.")
        isRunning = false
        subscription?.stop()
    }
}
