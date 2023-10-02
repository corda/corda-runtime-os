package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Captor
import org.mockito.Mockito.`when`
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StateManagerHelperTest {

    companion object {
        private const val TEST_KEY = "key"
        private val TEST_STATE_VALUE = StateType(1)
    }

    private data class StateType(val id: Int)
    private class EventType

    private val stateManager = mock<StateManager>()
    private val serializer = mock<CordaAvroSerializer<Any>>()
    private val stateDeserializer = mock<CordaAvroDeserializer<StateType>>()

    @Captor
    private val newStatesCaptor = argumentCaptor<Collection<State>>()

    @Captor
    private val updatedStatesCaptor = argumentCaptor<Collection<State>>()

    @BeforeEach
    fun setup() {
        `when`(serializer.serialize(anyOrNull())).thenAnswer { invocation ->
            val value = invocation.getArgument<Any>(0)
            serialized(value)
        }
    }

    private fun serialized(value: Any) = value.toString().toByteArray()

    @Test
    fun `successfully creates new state`() {

        val persistedSate: State? = null
        val newValue = StateType(1)
        val stateManagerHelper = StateManagerHelper<String, StateType, EventType>(
            stateManager,
            serializer,
            stateDeserializer,
        )

        val state = stateManagerHelper.createOrUpdateState(
            TEST_KEY, persistedSate, newValue
        )

        assertNotNull(state)
        assertEquals(TEST_KEY, state!!.key)
        assertArrayEquals(serialized(newValue), state.value)
        assertEquals(State.VERSION_INITIAL_VALUE, state.version)
        assertNotNull(state.metadata)
    }

    @Test
    fun `successfully updates existing state`() {
        val stateVersion = 5
        val persistedSate = State(
            TEST_KEY,
            serialized(TEST_STATE_VALUE),
            stateVersion,
            mock<Metadata>()
        )
        val updatedValue = StateType(TEST_STATE_VALUE.id + 1)
        val stateManagerHelper = StateManagerHelper<String, StateType, EventType>(
            stateManager,
            serializer,
            stateDeserializer,
        )

        val state = stateManagerHelper.createOrUpdateState(
            TEST_KEY, persistedSate, updatedValue
        )

        assertNotNull(state)
        assertEquals(persistedSate.key, state!!.key)
        assertArrayEquals(serialized(updatedValue), state.value)
        assertEquals(persistedSate.version, state.version)
        assertEquals(persistedSate.metadata, state.metadata)
    }

    @Test
    fun `successfully persists states`() {
        val stateManagerHelper = StateManagerHelper<String, StateType, EventType>(
            stateManager,
            serializer,
            stateDeserializer,
        )
        val states = listOf(
            State("1", "1".toByteArray(), 2),
            State("2", "2".toByteArray(), State.VERSION_INITIAL_VALUE),
            State("3", "3".toByteArray(), 3),
        )

        stateManagerHelper.persistStates(
            states.map { state ->
                ProcessorTask.Result(mock(), mock(), state)
            }
        )

        verify(stateManager).create(newStatesCaptor.capture())
        val capturedNewStates = newStatesCaptor.firstValue
        assertEquals(listOf(states[1]), capturedNewStates)
        verify(stateManager).update(updatedStatesCaptor.capture())
        val capturedUpdatedStates = updatedStatesCaptor.firstValue
        assertEquals(listOf(states[0], states[2]), capturedUpdatedStates)
    }

    @Test
    fun `successfully deserializes state`() {
        val stateManagerHelper = StateManagerHelper<String, StateType, EventType>(
            stateManager,
            serializer,
            stateDeserializer,
        )
        val serializedStateValue = "test".toByteArray()
        val state = mock<State>()
        `when`(state.value).thenReturn(serializedStateValue)

        stateManagerHelper.deserializeValue(state)

        verify(stateDeserializer).deserialize(serializedStateValue)
    }
}