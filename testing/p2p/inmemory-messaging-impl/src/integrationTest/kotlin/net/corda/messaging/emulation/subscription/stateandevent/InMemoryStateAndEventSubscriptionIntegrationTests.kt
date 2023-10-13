package net.corda.messaging.emulation.subscription.stateandevent

import com.typesafe.config.ConfigValueFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Timeout(10, unit = TimeUnit.SECONDS)
class InMemoryStateAndEventSubscriptionIntegrationTests {

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private enum class Event {
        CREATE_STATE,
        INCREASE_STATE,
        SEND_TO_ANOTHER_TOPIC,
        STOP,
    }
    private data class Key(val type: Int)
    private data class State(val number: Int)

    private val config = SmartConfigImpl.empty()
        .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(1000000))

    @Test
    fun `states and events going to the same partition`() {

        val publisherConfig = PublisherConfig("client")
        val count = 10
        val records = (1..count).map {
            Record("event.topic.state.and.events.one", Key(it), Event.CREATE_STATE)
        }
        val countDown = CountDownLatch(count)
        val states = ConcurrentHashMap<Int, Int>()

        val subscriptions = (1..4).map {
            val subscriptionConfig = SubscriptionConfig(
                eventTopic = "event.topic.state.and.events.one",
                groupName = "group",
            )
            val processor = object : StateAndEventProcessor<Key, State, Event> {
                override fun onNext(state: State?, event: Record<Key, Event>): StateAndEventProcessor.Response<State> {
                    if ((state != null) && (event.value == Event.STOP)) {
                        states[event.key.type] = state.number
                        countDown.countDown()
                    }
                    return if (event.value == Event.CREATE_STATE) {
                        StateAndEventProcessor.Response(
                            State(event.key.type),
                            listOf(
                                Record(
                                    subscriptionConfig.eventTopic,
                                    event.key,
                                    Event.STOP
                                )
                            )
                        )
                    } else {
                        StateAndEventProcessor.Response(State(event.key.type), emptyList())
                    }
                }

                override val keyClass = Key::class.java
                override val stateValueClass = State::class.java
                override val eventValueClass = Event::class.java
            }
            subscriptionFactory.createStateAndEventSubscription(
                subscriptionConfig = subscriptionConfig,
                processor = processor,
                messagingConfig = config
                    .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(it))
                    .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(1000000))
            )
        }.onEach {
            it.start()
        }

        publisherFactory.createPublisher(publisherConfig, config).use {
            it.publish(records)
        }
        countDown.await(1, TimeUnit.MINUTES)

        assertThat(states).isEqualTo((1..count).associateWith { it })

        subscriptions.forEach {
            it.close()
        }
    }

    @Test
    fun `events sends messages to another topic`() {
        val subscriptionConfig = SubscriptionConfig(
            eventTopic = "event.topic.test.one",
            groupName = "group1"
        )
        val anotherSubscriptionConfig = SubscriptionConfig(
            eventTopic = "event.topic.test.two",
            groupName = "group2"
        )

        val publisherConfig = PublisherConfig("client")
        val count = 10
        val increaseBy = 3
        val countDown = CountDownLatch(count * increaseBy)
        val sent = ConcurrentHashMap.newKeySet<Record<String, String>>()
        val got = ConcurrentHashMap.newKeySet<Record<String, String>>()
        val records = (1..count).map {
            Record(subscriptionConfig.eventTopic, Key(it), Event.SEND_TO_ANOTHER_TOPIC)
        }
        val processor = object : StateAndEventProcessor<Key, State, Event> {
            override fun onNext(state: State?, event: Record<Key, Event>): StateAndEventProcessor.Response<State> {
                return StateAndEventProcessor.Response(
                    null,
                    (1..increaseBy).map {
                        val toSend = Record(anotherSubscriptionConfig.eventTopic, UUID.randomUUID().toString(), UUID.randomUUID().toString())
                        sent.add(toSend)
                        toSend
                    }
                )
            }

            override val keyClass = Key::class.java
            override val stateValueClass = State::class.java
            override val eventValueClass = Event::class.java
        }
        val subscription = subscriptionFactory.createStateAndEventSubscription(
            subscriptionConfig = subscriptionConfig,
            processor = processor,
            messagingConfig = config
        )
        subscription.start()

        val anotherProcessor = object : StateAndEventProcessor<String, String, String> {
            override fun onNext(state: String?, event: Record<String, String>): StateAndEventProcessor.Response<String> {
                got.add(event)
                countDown.countDown()
                return StateAndEventProcessor.Response(null, emptyList())
            }

            override val keyClass = String::class.java
            override val stateValueClass = String::class.java
            override val eventValueClass = String::class.java
        }
        val anotherSubscription = subscriptionFactory.createStateAndEventSubscription(
            subscriptionConfig = anotherSubscriptionConfig,
            processor = anotherProcessor,
            messagingConfig = config
        )
        subscription.start()
        anotherSubscription.start()

        publisherFactory.createPublisher(publisherConfig, config).use {
            it.publish(records)
        }
        countDown.await(1, TimeUnit.MINUTES)

        assertThat(got).hasSize(count * increaseBy).isEqualTo(sent)

        subscription.close()
        anotherSubscription.close()
    }

    @Test
    fun `state modifications are saved`() {
        val subscriptionConfig = SubscriptionConfig(
            eventTopic = "event.topic.test.three",
            groupName = "group2"
        )

        val publisherConfig = PublisherConfig("client")
        val records = listOf(
            Record(subscriptionConfig.eventTopic, Key(1), Event.CREATE_STATE),
            Record(subscriptionConfig.eventTopic, Key(2), Event.CREATE_STATE),
            Record(subscriptionConfig.eventTopic, Key(3), Event.CREATE_STATE),
            Record(subscriptionConfig.eventTopic, Key(2), Event.INCREASE_STATE),
            Record(subscriptionConfig.eventTopic, Key(2), Event.INCREASE_STATE),
            Record(subscriptionConfig.eventTopic, Key(2), Event.INCREASE_STATE),
            Record(subscriptionConfig.eventTopic, Key(3), Event.INCREASE_STATE),
        )
        val countDown = CountDownLatch(records.size)
        val latestStates = ConcurrentHashMap<Key, State?>()
        val listener = object : StateAndEventListener<Key, State> {
            override fun onPartitionSynced(states: Map<Key, State>) {
            }

            override fun onPartitionLost(states: Map<Key, State>) {
            }

            override fun onPostCommit(updatedStates: Map<Key, State?>) {
                latestStates += updatedStates
            }
        }

        val processor = object : StateAndEventProcessor<Key, State, Event> {
            override fun onNext(state: State?, event: Record<Key, Event>): StateAndEventProcessor.Response<State> {
                val newState = when (event.value) {
                    Event.CREATE_STATE -> 1
                    Event.INCREASE_STATE -> (state!!.number + 1)
                    else -> throw Exception("Unexpected event!")
                }
                countDown.countDown()
                return StateAndEventProcessor.Response(
                    State(newState),
                    emptyList()
                )
            }

            override val keyClass = Key::class.java
            override val stateValueClass = State::class.java
            override val eventValueClass = Event::class.java
        }
        val subscription = subscriptionFactory.createStateAndEventSubscription(
            subscriptionConfig = subscriptionConfig,
            messagingConfig = config,
            processor = processor,
            stateAndEventListener = listener
        )

        subscription.start()

        publisherFactory.createPublisher(publisherConfig, config).use {
            it.publish(
                records
            )
        }
        countDown.await(1, TimeUnit.MINUTES)

        eventually {
            assertThat(latestStates[Key(4)]).isNull()
            assertThat(latestStates[Key(2)]).isEqualTo(State(4))
            assertThat(latestStates[Key(1)]).isEqualTo(State(1))
            assertThat(latestStates[Key(3)]).isEqualTo(State(2))
        }

        subscription.close()
    }

    @Test
    fun `states sends events to stateAndEventListener`() {
        val subscriptionConfig = SubscriptionConfig(
            eventTopic = "event.topic.test.four",
            groupName = "group3"
        )

        val publisherConfig = PublisherConfig("client")

        publisherFactory.createPublisher(publisherConfig, config).use {
            it.publish(
                listOf(
                    Record("${subscriptionConfig.eventTopic}.state", Key(5), State(10))
                )
            )
        }

        val records = listOf(
            Record(subscriptionConfig.eventTopic, Key(5), Event.INCREASE_STATE),
        )
        val countDown = CountDownLatch(2)
        var lostStates: Map<Key, State>? = null

        val listener = object : StateAndEventListener<Key, State> {
            override fun onPartitionSynced(states: Map<Key, State>) {
                if (states == mapOf(Key(5) to State(10))) {
                    countDown.countDown()
                }
            }

            override fun onPartitionLost(states: Map<Key, State>) {
                if (states.isNotEmpty()) {
                    lostStates = states
                }
            }

            override fun onPostCommit(updatedStates: Map<Key, State?>) {
                if (updatedStates == mapOf(Key(5) to State(11))) {
                    countDown.countDown()
                }
            }
        }
        val processor = object : StateAndEventProcessor<Key, State, Event> {
            override fun onNext(state: State?, event: Record<Key, Event>): StateAndEventProcessor.Response<State> {
                return StateAndEventProcessor.Response(
                    State(state?.number?.inc() ?: -1),
                    emptyList()
                )
            }

            override val keyClass = Key::class.java
            override val stateValueClass = State::class.java
            override val eventValueClass = Event::class.java
        }
        val subscription = subscriptionFactory.createStateAndEventSubscription(
            subscriptionConfig = subscriptionConfig,
            processor = processor,
            messagingConfig = config,
            stateAndEventListener = listener
        )

        subscription.start()

        publisherFactory.createPublisher(publisherConfig, config).use {
            it.publish(
                records
            )
        }
        countDown.await()
        assertThat(lostStates).isNull()

        subscription.close()
        assertThat(lostStates).containsEntry(Key(5), State(11))
    }
}
