package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.State.Companion.VERSION_INITIAL_VALUE
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StateManagerHelperTest {

    companion object {
        private const val TEST_KEY = "key"
        private val TEST_STATE_VALUE = StateType(1)
    }

    private data class StateType(val id: Int)

    private val stateManager = mock<StateManager>()
    private val stateSerializer = mock<CordaAvroSerializer<Any>>()
    private val stateDeserializer = mock<CordaAvroDeserializer<StateType>>()

    @BeforeEach
    fun setup() {
        `when`(stateSerializer.serialize(anyOrNull())).thenAnswer { invocation ->
            val value = invocation.getArgument<Any>(0)
            serialized(value)
        }
    }

    private fun serialized(value: Any) = value.toString().toByteArray()

    @Test
    fun `successfully creates new state`() {
        val persistedState: State? = null
        val newState = StateAndEventProcessor.State(
            StateType(1),
            Metadata(),
        )
        val stateManagerHelper = StateManagerHelper(
            stateManager,
            stateSerializer,
            stateDeserializer,
        )

        val state = stateManagerHelper.createOrUpdateState(
            TEST_KEY, persistedState, newState
        )

        assertNotNull(state)
        assertEquals(TEST_KEY, state!!.key)
        assertArrayEquals(serialized(newState.value!!), state.value)
        assertEquals(State.VERSION_INITIAL_VALUE, state.version)
        assertEquals(Metadata(mapOf(STATE_TYPE to StateType::class.java.name)), state.metadata)
    }

    @Test
    fun `successfully updates existing state`() {
        val stateVersion = 5
        val persistedState = State(
            TEST_KEY,
            serialized(TEST_STATE_VALUE),
            stateVersion,
            Metadata(mapOf(STATE_TYPE to StateType::class.java.simpleName))
        )
        val updatedState = StateAndEventProcessor.State(
            StateType(TEST_STATE_VALUE.id + 1),
            Metadata(),
        )
        val stateManagerHelper = StateManagerHelper(
            stateManager,
            stateSerializer,
            stateDeserializer,
        )

        val state = stateManagerHelper.createOrUpdateState(
            TEST_KEY, persistedState, updatedState
        )

        assertNotNull(state)
        assertEquals(persistedState.key, state!!.key)
        assertArrayEquals(serialized(updatedState.value!!), state.value)
        assertEquals(persistedState.version, state.version)
        assertEquals(Metadata(mapOf(STATE_TYPE to StateType::class.java.name)), state.metadata)
    }

    @Test
    fun `successfully deserializes state`() {
        val stateManagerHelper = StateManagerHelper(
            stateManager,
            stateSerializer,
            stateDeserializer,
        )
        val serializedStateValue = "test".toByteArray()
        val state = mock<State>()
        `when`(state.value).thenReturn(serializedStateValue)

        stateManagerHelper.deserializeValue(state)

        verify(stateDeserializer).deserialize(serializedStateValue)
    }

    @Test
    fun `marks state as failed when previous state exists`() {
        val stateVersion = 5
        val persistedState = State(
            TEST_KEY,
            serialized(TEST_STATE_VALUE),
            stateVersion,
            Metadata()
        )
        val stateManagerHelper = StateManagerHelper(
            stateManager,
            stateSerializer,
            stateDeserializer,
        )

        val state = stateManagerHelper.failStateProcessing(TEST_KEY, persistedState, "")

        assertEquals(persistedState.key, state.key)
        assertEquals(persistedState.version, state.version)
        assertTrue(state.metadata[PROCESSING_FAILURE] as Boolean)
    }

    @Test
    fun `marks state as failed when previous state does not exist`() {
        val stateManagerHelper = StateManagerHelper(
            stateManager,
            stateSerializer,
            stateDeserializer,
        )

        val state = stateManagerHelper.failStateProcessing(TEST_KEY, null, "")

        assertEquals(TEST_KEY, state.key)
        assertEquals(VERSION_INITIAL_VALUE, state.version)
        assertTrue(state.metadata[PROCESSING_FAILURE] as Boolean)
    }
}