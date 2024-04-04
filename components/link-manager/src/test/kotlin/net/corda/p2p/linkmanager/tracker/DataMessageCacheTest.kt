package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.ByteBuffer

class DataMessageCacheTest {
    private val knownKey = "test"
    private val valueBytes = knownKey.toByteArray()
    private val states = mapOf(
        knownKey to mock<State> {
            on { key } doReturn knownKey
            on { value } doReturn valueBytes
        }
    )
    private val createCaptor = argumentCaptor<Collection<State>>()
    private val deleteCaptor = argumentCaptor<Collection<State>>()
    private val stateManager = mock<StateManager> {
        on { name } doReturn mock()
        on { get(setOf(knownKey)) } doReturn states
        on { create(createCaptor.capture()) } doReturn emptySet()
        on { delete(deleteCaptor.capture()) } doReturn emptyMap()
    }
    private val appMessage = mock<AppMessage>()
    private val schemaRegistry = mock<AvroSchemaRegistry> {
        on { serialize(any()) } doReturn ByteBuffer.wrap(valueBytes)
        on { deserialize<AppMessage>(ByteBuffer.wrap(valueBytes)) } doReturn appMessage
    }
    private val configDominoTile = mock<ComplexDominoTile> {
        on { coordinatorName } doReturn mock()
    }
    private val config = mock<DeliveryTrackerConfiguration> {
        on { dominoTile } doReturn configDominoTile
        on { config } doReturn DeliveryTrackerConfiguration.Configuration(
            maxCacheSizeMegabytes = 100,
            maxCacheOffsetAge = 50000,
            statePersistencePeriodSeconds = 1.0,
            outboundBatchProcessingTimeoutSeconds = 30.0,
            maxNumberOfPersistenceRetries = 3,
        )
    }
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val followStatusChangesByNameHandlers = mutableSetOf<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { followStatusChangesByName(any()) } doAnswer {
            val handler = mock<RegistrationHandle>()
            followStatusChangesByNameHandlers.add(handler)
            handler
        }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val cache = DataMessageCache(
        coordinatorFactory,
        stateManager,
        schemaRegistry,
        config,
    )

    @Test
    fun `onStart will listen to configuration changes`() {
        followStatusChangesByNameHandlers.forEach { followStatusChangesByNameHandler ->
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    followStatusChangesByNameHandler,
                    LifecycleStatus.UP,
                ),
                coordinator,
            )
        }

        verify(config).lister(cache)
    }

    @Nested
    inner class GetTests {
        @Test
        fun `get can return a value previously cached using put`() {
            val testMessage = mock<AppMessage>()
            val key = "key"
            cache.put(key, testMessage, DataMessageCache.PartitionAndOffset(1, 1))

            val result = cache.get(key)

            assertThat(result).isEqualTo(testMessage)
        }

        @Test
        fun `get will query the state manager if entry is not in the cache`() {
            val result = cache.get(knownKey)

            assertThat(result).isEqualTo(appMessage)
            verify(stateManager).get(setOf(knownKey))
        }
    }

    @Nested
    inner class PutTests {
        @Test
        fun `put will persist older cache entries to state manager`() {
            val testMessage = mock<AppMessage>()
            val key1 = "key-1"
            val key2 = "key-2"
            cache.put(key1, testMessage, DataMessageCache.PartitionAndOffset(1, 10))

            cache.put(key2, testMessage, DataMessageCache.PartitionAndOffset(1, 55000))

            assertThat(createCaptor.firstValue.single().key).isEqualTo(key1)
        }
    }

    @Nested
    inner class InvalidateTests {
        @Test
        fun `invalidate will delete from state manager if key not in cache`() {
            cache.invalidate(knownKey)

            assertThat(deleteCaptor.firstValue.single().key).isEqualTo(knownKey)
        }

        @Test
        fun `invalidate discards previously added cache entry`() {
            val testMessage = mock<AppMessage>()
            val key = "key"
            cache.put(key, testMessage, DataMessageCache.PartitionAndOffset(1, 1))

            cache.invalidate(key)

            assertThat(cache.get(key)).isNull()
        }
    }
}
