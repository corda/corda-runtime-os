package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.p2p.linkmanager.tracker.exception.DataMessageStoreException
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.AssertionsForClassTypes.assertThatCode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class DataMessageStoreTest {
    private val ids = listOf("id1", "id2")
    private val states = ids.associateWith { id ->
        mock<State> {
            on { value } doReturn id.toByteArray()
        }
    }
    private val createdStates = argumentCaptor<Collection<State>>()
    private val stateManager = mock<StateManager> {
        on { get(listOf("P2P-msg:id1", "P2P-msg:id2")) } doReturn states
        on { create(createdStates.capture()) } doReturn emptySet()
        on { delete(any()) } doReturn emptyMap()
    }
    private val messageHeader = mock<AuthenticatedMessageHeader> {
        on { messageId } doReturn "id2"
    }
    private val authenticatedMessage = mock<AuthenticatedMessage> {
        on { header } doReturn messageHeader
    }
    private val appMessageWithoutId = mock<AppMessage>()
    private val appMessageWithId = mock<AppMessage> {
        on { message } doReturn authenticatedMessage
    }
    private val schemaRegistry = mock<AvroSchemaRegistry> {
        on {
            deserialize(
                ByteBuffer.wrap(ids[0].toByteArray()),
                AppMessage::class.java,
                null,
            )
        } doReturn appMessageWithoutId
        on {
            deserialize(
                ByteBuffer.wrap(ids[1].toByteArray()),
                AppMessage::class.java,
                null,
            )
        } doReturn appMessageWithId
        on { serialize(appMessageWithoutId) } doReturn ByteBuffer.wrap(ids[0].toByteArray())
        on { serialize(appMessageWithId) } doReturn ByteBuffer.wrap(ids[1].toByteArray())
    }

    private val store = DataMessageStore(
        stateManager = stateManager,
        schemaRegistry = schemaRegistry,
    )

    @Nested
    inner class ReadTests {
        @Test
        fun `read with empty list will not invoke the state manager`() {
            store.read(emptyList())

            verifyNoInteractions(stateManager)
        }

        @Test
        fun `read with empty list returns empty list`() {
            val ret = store.read(emptyList())

            assertThat(ret).isEmpty()
        }

        @Test
        fun `read returns the correct data`() {
            val ret = store.read(ids)

            assertThat(ret).containsExactlyInAnyOrder(appMessageWithoutId, appMessageWithId)
        }
    }

    @Nested
    inner class WriteTests {
        @Test
        fun `write with empty list will not invoke the state manager`() {
            store.write(emptyList())

            verifyNoInteractions(stateManager)
        }

        @Test
        fun `write will write the correct values`() {
            store.write(listOf(appMessageWithoutId, appMessageWithId))

            val createdStates = createdStates.allValues.flatten()
            assertThat(createdStates)
                .hasSize(1)
                .anySatisfy {
                    assertThat(it.key).isEqualTo("P2P-msg:id2")
                }.anySatisfy {
                    assertThat(String(it.value)).isEqualTo("id2")
                }.anySatisfy {
                    assertThat(it.version).isEqualTo(State.VERSION_INITIAL_VALUE)
                }
        }

        @Test
        fun `write will not write messages without ID`() {
            store.write(listOf(appMessageWithoutId, appMessageWithId))

            val statesKeys = createdStates.allValues.flatten().map {
                it.key
            }
            assertThat(statesKeys).doesNotContain("P2P-msg:id1")
        }

        @Test
        fun `write will not throw an exception if it there was an unexpected error`() {
            whenever(stateManager.create(any())).doReturn(setOf("id1"))

            assertThatCode {
                store.write(listOf(appMessageWithoutId, appMessageWithId))
            }.doesNotThrowAnyException()
        }
    }

    @Nested
    inner class DeleteTests {
        @Test
        fun `delete with empty list will not invoke the state manager`() {
            store.delete(emptyList())

            verifyNoInteractions(stateManager)
        }

        @Test
        fun `delete will delete the correct values`() {
            store.delete(ids)

            verify(stateManager).delete(states.values)
        }

        @Test
        fun `delete will throw an exception if it there was an unexpected error`() {
            whenever(stateManager.delete(any())).doReturn(mapOf("id1" to mock()))

            assertThatThrownBy {
                store.delete(ids)
            }.isExactlyInstanceOf(DataMessageStoreException::class.java)
        }

        @Test
        fun `delete will not try to delete if there nothing to delete`() {
            whenever(stateManager.get(any())).doReturn(emptyMap())

            store.delete(ids)

            verify(stateManager, never()).delete(any())
        }
    }
}
