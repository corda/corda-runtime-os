package net.corda.messaging.kafka.integration.subscription

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(ServiceExtension::class)
class InMemoryStateAndEventSubscriptionIntegrationStamTests {
    data class Event(val name: String, val index: Int)
    data class Key(val type: String)
    data class State(val number: Double)

    private val clientId = "testId"

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val config = SubscriptionConfig(
        eventTopic = "in.mem.state.and.event.topic",
        groupName = "in.mem.state.and.event.group"
    )

    private val statesMap = ConcurrentHashMap<Key, State>()
    private val receivedRecords = CopyOnWriteArrayList<Record<Key, Event>>()

    private inner class Listener : StateAndEventProcessor<Key, State, Event> {
        private val stopMe = AtomicInteger(3)
        override fun onNext(state: State?, event: Record<Key, Event>): StateAndEventProcessor.Response<State> {
            if (state != null) {
                statesMap[event.key] = state
            }
            receivedRecords.add(event)
            val stateNumber = state?.number ?: 8.0
            println("QQQ stateNumber - $stateNumber")
            if ((stateNumber > 0)&&(stopMe.decrementAndGet() > 0)) {
                val records = (1..3).map {
                    Record(config.eventTopic, Key("hello $stateNumber"), Event("type", 3))
                }
                println("QQQ sending new state ${State(stateNumber - 1.0)}")
                return StateAndEventProcessor.Response(State(stateNumber - 1.0), records)
            } else {
                return StateAndEventProcessor.Response(null, emptyList())
            }
        }

        override val keyClass = Key::class.java
        override val stateValueClass = State::class.java
        override val eventValueClass = Event::class.java
    }

    @Test
    fun `test state and event subscription`() {
        println("QQQ A1")
        val subscriptions = (1..4).map {
            subscriptionFactory.createStateAndEventSubscription(subscriptionConfig = config, Listener()).also { it.start() }
        }
        println("QQQ A2")

        val publisherConfig = PublisherConfig(clientId)
        val records = listOf(
            Record(
                config.eventTopic,
                Key("one"),
                Event("type", 1)
            )
        )
        publisherFactory.createPublisher(publisherConfig).use {
            it.publish(records)
        }
        println("QQQ A3")

        (1..100).forEach {
            println("QQQ A4 ${receivedRecords.size}")
            Thread.sleep(1000)
        }
        subscriptions.forEach {
            it.stop()
        }

        println("QQQ statesMap = $statesMap")
        println("QQQ receivedRecords = ${receivedRecords.size}")
    }
}
