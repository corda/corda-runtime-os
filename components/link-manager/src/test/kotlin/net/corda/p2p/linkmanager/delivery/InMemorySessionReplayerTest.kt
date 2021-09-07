package net.corda.p2p.linkmanager.delivery

import com.typesafe.config.Config
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.MockNetworkMap
import net.corda.p2p.schema.Schema
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import java.security.KeyPairGenerator
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class InMemorySessionReplayerTest {

    companion object {
        private const val GROUP_ID = "myGroup"
        private val US = LinkManagerNetworkMap.HoldingIdentity("Us",GROUP_ID)
        private val COUNTER_PARTY = LinkManagerNetworkMap.HoldingIdentity("CounterParty", GROUP_ID)
        private val MAX_MESSAGE_SIZE = 100000
        lateinit var loggingInterceptor: LoggingInterceptor

        private val KEY_PAIR = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).genKeyPair()
        private val replayPeriod = Duration.ofMillis(2)

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    val netMap = MockNetworkMap(listOf(US, COUNTER_PARTY)).getSessionNetworkMapForNode(US)

    class SinglePhaseTestListBasedPublisher(private val totalInvocations: Int): Publisher {

        var list = mutableListOf<Record<*, *>>()
        var testWaitLatch = CountDownLatch(1)
        var publisherWaitLatch = CountDownLatch(1)
        private var invocations = 0

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
            throw RuntimeException("publishToPartition should never be called in this test.")
        }

        override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            if (invocations == totalInvocations) {
                testWaitLatch.countDown()
                publisherWaitLatch.await()
            }
            invocations++
            list.addAll(records)
            return emptyList()
        }

        override fun close() {
            throw RuntimeException("close should never be called in this test.")
        }
    }

    @Test
    fun `InMemorySessionReplayer replays added session message repeatidly`() {
        val totalReplays = 5
        val publisher = SinglePhaseTestListBasedPublisher(totalReplays)
        val publisherFactory = object : PublisherFactory {
            override fun createPublisher(publisherConfig: PublisherConfig, nodeConfig: Config): Publisher {
                return publisher
            }
        }
        val replayer = InMemorySessionReplayer(Duration.ofMillis(1), publisherFactory, netMap)
        val id = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        replayer.start()
        replayer.addMessageForReplay(id, SessionReplayer.SessionMessageReplay(helloMessage, COUNTER_PARTY))

        publisher.testWaitLatch.await()
        assertEquals(totalReplays, publisher.list.size)
        for (record in publisher.list) {
            assertEquals(Schema.LINK_OUT_TOPIC, record.topic)
            assertTrue(record.value is LinkOutMessage)
            assertSame(helloMessage, (record.value as LinkOutMessage).payload)
        }

        publisher.publisherWaitLatch.countDown()
        replayer.stop()
    }

    class TwoPhaseTestListBasedPublisher(
        private val firstPhaseInvocations: Int,
        private val secondPhaseInvocations: Int
    ): Publisher {

        var list = mutableListOf<Record<*, *>>()
        var testWaitLatch = CountDownLatch(1)
        var publisherWaitLatch = CountDownLatch(1)
        var testWaitLatch1 = CountDownLatch(1)
        var publisherWaitLatch1 = CountDownLatch(1)

        private var invocations = 0

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
            throw RuntimeException("publishToPartition should never be called in this test.")
        }

        override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            if (invocations == firstPhaseInvocations) {
                testWaitLatch.countDown()
                publisherWaitLatch.await()
            }
            if (invocations == firstPhaseInvocations + secondPhaseInvocations) {
                testWaitLatch1.countDown()
                publisherWaitLatch1.await()
            }
            invocations++
            list.addAll(records)
            return emptyList()
        }

        override fun close() {
            throw RuntimeException("close should never be called in this test.")
        }
    }

    @Test
    fun `InMemorySessionReplayer stops replaying when a message is removed`() {
        val initialReplays = 6
        val furtherReplays = 4
        val publisher = TwoPhaseTestListBasedPublisher(initialReplays, furtherReplays)
        val publisherFactory = object : PublisherFactory {
            override fun createPublisher(publisherConfig: PublisherConfig, nodeConfig: Config): Publisher {
                return publisher
            }
        }
        val replayer = InMemorySessionReplayer(Duration.ofMillis(50), publisherFactory, netMap)
        val firstId = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            firstId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        val secondId = UUID.randomUUID().toString()
        val secondHelloMessage = AuthenticationProtocolInitiator(
            secondId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        replayer.start()

        replayer.addMessageForReplay(
            firstId,
            SessionReplayer.SessionMessageReplay(helloMessage, COUNTER_PARTY)
        )
        replayer.addMessageForReplay(
            secondId,
            SessionReplayer.SessionMessageReplay(secondHelloMessage, COUNTER_PARTY)
        )

        publisher.testWaitLatch.await()
        assertEquals(initialReplays, publisher.list.size)

        for (i in 0 until publisher.list.size) {
            val record = publisher.list[i]
            assertEquals(Schema.LINK_OUT_TOPIC, record.topic)
            assertTrue(record.value is LinkOutMessage)
            if (i % 2 == 0) assertSame(helloMessage, (record.value as LinkOutMessage).payload)
            else assertSame(secondHelloMessage, (record.value as LinkOutMessage).payload)
        }
        replayer.removeMessageFromReplay(firstId)
        publisher.publisherWaitLatch.countDown()
        publisher.testWaitLatch1.await()

        assertEquals(initialReplays + furtherReplays, publisher.list.size)

        for (i in initialReplays until furtherReplays) {
            val record = publisher.list[i]
            assertEquals(Schema.LINK_OUT_TOPIC, record.topic)
            assertTrue(record.value is LinkOutMessage)
            assertSame(secondHelloMessage, (record.value as LinkOutMessage).payload)
        }

        publisher.publisherWaitLatch1.countDown()
        replayer.stop()
    }

    @Test
    fun `InMemorySessionReplayer logs a warning when our network type is not in the network map`() {
        val totalReplays = 1
        val publisher = SinglePhaseTestListBasedPublisher(totalReplays)
        val publisherFactory = object : PublisherFactory {
            override fun createPublisher(publisherConfig: PublisherConfig, nodeConfig: Config): Publisher {
                return publisher
            }
        }

        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(null).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(mockNetworkMap.getMemberInfo(COUNTER_PARTY)).thenReturn(netMap.getMemberInfo(COUNTER_PARTY))

        val replayer = InMemorySessionReplayer(Duration.ofMillis(1), publisherFactory, mockNetworkMap)
        val id = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        replayer.start()
        replayer.addMessageForReplay(id, SessionReplayer.SessionMessageReplay(helloMessage, COUNTER_PARTY))
        publisher.testWaitLatch.await()
        assertEquals(1, publisher.list.size)
        val record = publisher.list.single()
        assertEquals(Schema.LINK_OUT_TOPIC, record.topic)
        assertTrue(record.value is LinkOutMessage)

        publisher.publisherWaitLatch.countDown()
        replayer.stop()

        loggingInterceptor.assertSingleWarning("Attempted to replay a session negotiation message (type " +
            "${InitiatorHelloMessage::class.java.simpleName}) but could not find the network type in the NetworkMap for group" +
            " $GROUP_ID. The message was not replayed.")
    }

    @Test
    fun `InMemorySessionReplayer logs a warning when the responder is not in the network map`() {
        val totalReplays = 1
        val publisher = SinglePhaseTestListBasedPublisher(totalReplays)
        val publisherFactory = object : PublisherFactory {
            override fun createPublisher(publisherConfig: PublisherConfig, nodeConfig: Config): Publisher {
                return publisher
            }
        }

        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(mockNetworkMap.getMemberInfo(COUNTER_PARTY)).thenReturn(null).thenReturn(netMap.getMemberInfo(COUNTER_PARTY))

        val replayer = InMemorySessionReplayer(Duration.ofMillis(1), publisherFactory, mockNetworkMap)
        val id = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
            ).generateInitiatorHello()

        replayer.start()
        replayer.addMessageForReplay(id, SessionReplayer.SessionMessageReplay(helloMessage, COUNTER_PARTY))
        publisher.testWaitLatch.await()
        assertEquals(1, publisher.list.size)
        val record = publisher.list.single()
        assertEquals(Schema.LINK_OUT_TOPIC, record.topic)
        assertTrue(record.value is LinkOutMessage)

        publisher.publisherWaitLatch.countDown()
        replayer.stop()

        loggingInterceptor.assertSingleWarning("Attempted to replay a session negotiation message (type " +
            "${InitiatorHelloMessage::class.java.simpleName}) with peer $COUNTER_PARTY which is not in the network" +
            " map. The message was not replayed.")
    }

    @Test
    fun `The InMemorySessionReplayer will not replay before start`() {
        val publisher = Mockito.mock(Publisher::class.java)
        val publisherFactory = object : PublisherFactory {
            override fun createPublisher(publisherConfig: PublisherConfig, nodeConfig: Config): Publisher {
                return publisher
            }
        }
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val helloMessage = AuthenticationProtocolInitiator(
            "",
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()
        val replayer = InMemorySessionReplayer(Duration.ofMillis(1), publisherFactory, mockNetworkMap)
        assertThrows<MessageAddedForReplayWhenNotStartedException> {
            replayer.addMessageForReplay("", SessionReplayer.SessionMessageReplay(helloMessage, COUNTER_PARTY))
        }
    }
}