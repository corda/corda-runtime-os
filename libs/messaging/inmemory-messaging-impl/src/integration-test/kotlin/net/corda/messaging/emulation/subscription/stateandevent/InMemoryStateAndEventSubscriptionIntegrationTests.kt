package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class InMemoryStateAndEventSubscriptionIntegrationTests {
    private enum class Event {
        CREATE_STATE,
        INCREASE_STATE,
        SEND_TO_ANOTHER_TOPIC,
        STOP,
    }
    private data class Key(val type: Int)
    private data class State(val number: Int)
    private data class OtherTopicEvent(val type: Int, val index: Int)

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val config = SubscriptionConfig(
        eventTopic = "in.mem.state.and.event.topic",
        groupName = "in.mem.state.and.event.group"
    )

    private val anotherTopic = "in.mem.another.topic"

    private val statesMap = ConcurrentHashMap<Key, State>()
    private val eventsLatch = CountDownLatch(31)

    private inner class Listener : StateAndEventProcessor<Key, State, Event> {
        override fun onNext(state: State?, event: Record<Key, Event>): StateAndEventProcessor.Response<State> {
            if (state != null) {
                statesMap[event.key] = state
            }
            val type = event.key.type
            eventsLatch.countDown()
            if (event.key.type > 10) {
                return StateAndEventProcessor.Response(
                    null, emptyList()
                )
            }
            return when (event.value) {
                Event.CREATE_STATE -> {
                    StateAndEventProcessor.Response(
                        State(event.key.type - 2),
                        listOf(
                            Record(
                                config.eventTopic,
                                Key(type + 1),
                                Event.CREATE_STATE
                            ),
                            Record(
                                config.eventTopic,
                                Key(type),
                                Event.INCREASE_STATE
                            )
                        ),
                    )
                }
                Event.INCREASE_STATE -> {
                    val newState = State(state?.number!! + 1)
                    if (state.number < event.key.type) {
                        StateAndEventProcessor.Response(
                            newState,
                            listOf(
                                Record(
                                    config.eventTopic,
                                    Key(type),
                                    Event.INCREASE_STATE
                                )
                            ),
                        )
                    } else {
                        StateAndEventProcessor.Response(
                            newState,
                            listOf(
                                Record(
                                    config.eventTopic,
                                    Key(type),
                                    Event.SEND_TO_ANOTHER_TOPIC
                                )
                            ),
                        )
                    }
                }
                Event.SEND_TO_ANOTHER_TOPIC -> {
                    StateAndEventProcessor.Response(
                        state,
                        (1..3).map {
                            Record(anotherTopic, "test", OtherTopicEvent(type, it))
                        } + Record(
                            config.eventTopic,
                            Key(type),
                            Event.STOP
                        )
                    )
                }
                else -> {
                    StateAndEventProcessor.Response(
                        state, emptyList()
                    )
                }
            }
        }

        override val keyClass = Key::class.java
        override val stateValueClass = State::class.java
        override val eventValueClass = Event::class.java
    }
    @Test
    fun `test state and event subscription`() {
        val subscriptions = (1..4).map {
            subscriptionFactory.createStateAndEventSubscription(
                subscriptionConfig = config,
                processor = Listener(),
            ).also { it.start() }
        }

        val publisherConfig = PublisherConfig("client")
        val records = listOf(
            Record(
                config.eventTopic,
                Key(6),
                Event.CREATE_STATE
            )
        )
        publisherFactory.createPublisher(publisherConfig).use {
            it.publish(records)
        }

        eventsLatch.await(25, TimeUnit.SECONDS)

        subscriptions.forEach {
            it.stop()
        }

        assertThat(statesMap).isEqualTo(
            (6..10).associate {
                Key(it) to State(it + 1)
            }
        )

        val otherTopicEventsLatch = CountDownLatch(15)
        val otherTopicsEvents = ConcurrentHashMap.newKeySet<OtherTopicEvent>()
        val otherSubscription =
            subscriptionFactory.createEventLogSubscription(
                SubscriptionConfig("other.group", anotherTopic),
                object : EventLogProcessor<String, OtherTopicEvent> {

                    override val keyClass = String::class.java
                    override val valueClass = OtherTopicEvent::class.java
                    override fun onNext(events: List<EventLogRecord<String, OtherTopicEvent>>): List<Record<*, *>> {
                        events.forEach {
                            otherTopicsEvents.add(it.value)
                            otherTopicEventsLatch.countDown()
                        }

                        return emptyList()
                    }
                },
                partitionAssignmentListener = null
            )
        otherSubscription.start()
        otherTopicEventsLatch.await(1, TimeUnit.SECONDS)
        otherSubscription.stop()
        assertThat(otherTopicsEvents).containsAll(
            (1..3).flatMap { index ->
                (6..10).map { type ->
                    OtherTopicEvent(type, index)
                }
            }
        )
    }
}
