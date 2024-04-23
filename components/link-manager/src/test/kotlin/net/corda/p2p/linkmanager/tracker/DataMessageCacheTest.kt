package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

class DataMessageCacheTest {
    private val knownKey = "test"
    private val valueBytes = knownKey.toByteArray()
    private val metadata = Metadata(
        mapOf(
            "OFFSET" to 1L,
            "PARTITION" to 20,
        ),
    )
    private val states = mapOf(
        knownKey to State(
            key = knownKey,
            value = valueBytes,
            metadata = metadata,
        ),
    )
    private val createCaptor = argumentCaptor<Collection<State>>()
    private val deleteCaptor = argumentCaptor<Collection<State>>()
    private val stateManager = mock<StateManager> {
        on { name } doReturn mock()
        on { get(setOf(knownKey)) } doReturn states
        on { create(createCaptor.capture()) } doReturn emptySet()
        on { delete(deleteCaptor.capture()) } doReturn emptyMap()
    }
    private val message = mock<AuthenticatedMessage>()
    private val schemaRegistry = mock<AvroSchemaRegistry> {
        on { serialize(any()) } doReturn ByteBuffer.wrap(valueBytes)
        on { deserialize<AuthenticatedMessage>(ByteBuffer.wrap(valueBytes)) } doReturn message
    }
    private val configDominoTile = mock<ComplexDominoTile> {
        on { coordinatorName } doReturn mock()
    }
    private val config = mock<DeliveryTrackerConfiguration> {
        on { dominoTile } doReturn configDominoTile
        on { config } doReturn DeliveryTrackerConfiguration.Configuration(
            maxCacheOffsetAge = 300,
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
    private val commonComponents = mock<CommonComponents> {
        on { lifecycleCoordinatorFactory } doReturn coordinatorFactory
        on { stateManager } doReturn stateManager
        on { schemaRegistry } doReturn schemaRegistry
    }
    private val onOffsetsToReadFromChanged = ConcurrentLinkedQueue<Pair<Int, Long>>()

    private val cache = DataMessageCache(
        commonComponents,
        config,
    ) {
        onOffsetsToReadFromChanged.addAll(it)
    }

    private fun createTestRecord(
        id: String,
        partition: Int = 1,
        offset: Long = 1,
    ): MessageRecord {
        val headers = mock<AuthenticatedMessageHeader> {
            on { messageId } doReturn id
        }
        val message = mock<AuthenticatedMessage> {
            on { header } doReturn headers
        }
        return MessageRecord(
            message = message,
            partition = partition,
            offset = offset,
        )
    }

    @Nested
    inner class GetTests {
        @Test
        fun `get can return a value previously cached using put`() {
            val key = "key"
            val records = listOf(createTestRecord(key))
            cache.put(records)

            val result = cache.get(key)

            assertThat(result).isEqualTo(records.first().message)
            verify(stateManager, never()).get(setOf(knownKey))
        }

        @Test
        fun `get will query the state manager if entry is not in the cache`() {
            val result = cache.get(knownKey)

            assertThat(result).isEqualTo(message)
            verify(stateManager).get(setOf(knownKey))
        }

        @Test
        fun `get will return nothing for unknown key`() {
            val result = cache.get("nop")

            assertThat(result).isNull()
        }

        @Test
        fun `get will return nothing for error during read`() {
            whenever(stateManager.get(setOf("nop"))).doThrow(CordaRuntimeException("Ooops"))

            val result = cache.get("nop")

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class PutTests {
        @Test
        fun `put without messages won't throw an exception`() {
            assertThatCode {
                cache.put(emptyList())
            }.doesNotThrowAnyException()
        }

        @Test
        fun `put will keep the records in cache`() {
            cache.put(
                listOf(
                    createTestRecord(
                        id = "one",
                        offset = 10,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "two",
                        offset = 10,
                        partition = 2,
                    ),
                    createTestRecord(
                        id = "three",
                        offset = 20,
                        partition = 1,
                    ),
                ),
            )

            assertThat(cache.get("two")).isNotNull()
        }

        @Test
        fun `put will not persist if not needed`() {
            cache.put(
                listOf(
                    createTestRecord(
                        id = "one",
                        offset = 10,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "two",
                        offset = 10,
                        partition = 2,
                    ),
                    createTestRecord(
                        id = "three",
                        offset = 20,
                        partition = 1,
                    ),
                ),
            )

            verify(stateManager, never()).create(any())
        }

        @Test
        fun `put will persist the correct message`() {
            val persisted = argumentCaptor<Collection<State>>()
            whenever(stateManager.create(persisted.capture())).doReturn(emptySet())
            cache.put(
                listOf(
                    createTestRecord(
                        id = "1",
                        offset = 1,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "another-partition",
                        offset = 1,
                        partition = 2,
                    ),
                ),
            )

            cache.put(
                listOf(
                    createTestRecord(
                        id = "2",
                        offset = 2,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "3",
                        offset = 3,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "4",
                        offset = 4,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "299",
                        offset = 299,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "300",
                        offset = 300,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "301",
                        offset = 301,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "302",
                        offset = 302,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "303",
                        offset = 303,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "304",
                        offset = 304,
                        partition = 1,
                    ),
                ),
            )

            val persistedIds = persisted.firstValue.map { it.key }
            assertThat(persistedIds).contains(
                "1",
                "2",
                "3",
            )
        }

        @Test
        fun `put will update the offsets to read from`() {
            cache.put(
                listOf(
                    createTestRecord(
                        id = "another-partition",
                        offset = 20,
                        partition = 2,
                    ),
                    createTestRecord(
                        id = "3",
                        offset = 3,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "4",
                        offset = 4,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "299",
                        offset = 299,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "300",
                        offset = 300,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "301",
                        offset = 301,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "302",
                        offset = 302,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "303",
                        offset = 303,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "504",
                        offset = 504,
                        partition = 1,
                    ),
                ),
            )

            assertThat(onOffsetsToReadFromChanged).containsExactly(
                1 to 299,
                2 to 20,
            )
        }

        @Test
        fun `put will set the tile in error state in case of an exception`() {
            val error = CordaRuntimeException("Ooops")
            whenever(stateManager.create(any())).doThrow(error)

            cache.put(
                listOf(
                    createTestRecord(
                        id = "3",
                        offset = 3,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "504",
                        offset = 504,
                        partition = 1,
                    ),
                ),
            )

            verify(coordinator).postEvent(isA<ErrorEvent>())
        }

        @Test
        fun `put will ignore the state manager reply`() {
            whenever(stateManager.create(any())).doReturn(setOf("3"))

            cache.put(
                listOf(
                    createTestRecord(
                        id = "3",
                        offset = 3,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "504",
                        offset = 504,
                        partition = 1,
                    ),
                ),
            )

            verify(coordinator, never()).postEvent(isA<ErrorEvent>())
        }
    }

    @Nested
    inner class RemoveTests {
        @Test
        fun `remove will delete from state manager if key not in cache`() {
            cache.remove(knownKey)

            assertThat(deleteCaptor.firstValue.single().key).isEqualTo(knownKey)
        }

        @Test
        fun `remove discards previously added cache entry`() {
            val key = "key"
            cache.put(
                listOf(
                    createTestRecord(key),
                ),
            )

            cache.remove(key)

            assertThat(cache.get(key)).isNull()
        }

        @Test
        fun `remove will notify about offset to read change if changed`() {
            cache.put(
                listOf(
                    createTestRecord(
                        id = "2",
                        offset = 2,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "3",
                        offset = 3,
                        partition = 1,
                    ),
                    createTestRecord(
                        id = "4",
                        offset = 4,
                        partition = 1,
                    ),
                ),
            )

            cache.remove("2")

            assertThat(onOffsetsToReadFromChanged).contains(1 to 3)
        }

        @Test
        fun `remove will notify about offset to read change if empty`() {
            cache.put(
                listOf(
                    createTestRecord(
                        id = "4",
                        offset = 4,
                        partition = 1,
                    ),
                ),
            )

            cache.remove("4")

            assertThat(onOffsetsToReadFromChanged).contains(1 to 5)
        }

        @Test
        fun `remove will return the record if found`() {
            cache.put(
                listOf(
                    createTestRecord(
                        id = "4",
                        offset = 4,
                        partition = 1,
                    ),
                ),
            )

            val record = cache.remove("4")

            assertThat(record).isNotNull
        }

        @Test
        fun `remove will return null if not found`() {
            val record = cache.remove("key")

            assertThat(record).isNull()
        }

        @Test
        fun `remove will try again if the state manager failed the first time`() {
            whenever(stateManager.delete(any()))
                .thenReturn(mapOf("key" to mock()))
                .thenReturn(emptyMap())

            cache.remove(knownKey)

            verify(stateManager, times(2)).delete(states.values)
        }

        @Test
        fun `remove will stop trying after a while`() {
            whenever(stateManager.delete(any()))
                .doReturn(mapOf("key" to mock()))

            val record = cache.remove(knownKey)

            assertThat(record).isNull()
        }

        @Test
        fun `remove will try once if the state manager had an exception`() {
            whenever(stateManager.delete(any()))
                .thenThrow(CordaRuntimeException("oops"))

            cache.remove(knownKey)

            verify(stateManager).delete(states.values)
        }

        @Test
        fun `remove will return the state event if it failed to delete it`() {
            whenever(stateManager.delete(any()))
                .doThrow(CordaRuntimeException("Nop"))

            val record = cache.remove(knownKey)

            assertThat(record).isNotNull()
        }
    }
}
