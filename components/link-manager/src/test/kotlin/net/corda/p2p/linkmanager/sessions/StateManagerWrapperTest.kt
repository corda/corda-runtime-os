package net.corda.p2p.linkmanager.sessions

import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.linkmanager.state.SessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StateManagerWrapperTest {
    private val keys = setOf("key1", "key2")
    private val state = mock<State> {
        on { metadata } doReturn Metadata(emptyMap())
    }
    private val filter = mock<MetadataFilter>()
    private val filters = listOf(filter)
    private val keysToStates = mapOf("key1" to state)
    private val stateManager = mock<StateManager> {
        on { get(keys) } doReturn keysToStates
        on { findByMetadataMatchingAny(filters) } doReturn keysToStates
    }
    private val sessionCache = mock<SessionCache> {
        on { validateStatesAndScheduleExpiry(keysToStates) } doReturn keysToStates
    }
    private val sessionState = mock<SessionState>()
    private val checkRevocation = mock<CheckRevocation>()
    private val reEstablishmentMessageSender = mock<ReEstablishmentMessageSender>()
    private val stateConvertor = mock<StateConvertor> {
        on {
            toCordaSessionState(
                state,
                checkRevocation,
            )
        } doReturn sessionState
    }

    private val wrapper = StateManagerWrapper(
        stateManager,
        sessionCache,
        stateConvertor,
        checkRevocation,
        reEstablishmentMessageSender,
    )

    @Test
    fun `get calls the state manager`() {
        wrapper.get(keys)

        verify(stateManager).get(keys)
    }

    @Test
    fun `get calls the scheduler`() {
        wrapper.get(keys)

        verify(sessionCache).validateStatesAndScheduleExpiry(keysToStates)
    }

    @Test
    fun `get returns the correct value`() {
        val ret = wrapper.get(keys)

        assertThat(ret).isEqualTo(
            mapOf(
                "key1" to StateManagerWrapper.StateManagerSessionState(
                    state,
                    sessionState,
                )
            )
        )
    }

    @Test
    fun `get will not return unrecoverable state`() {
        whenever(stateConvertor.toCordaSessionState(state, checkRevocation)).thenReturn(null)
        val ret = wrapper.get(keys)

        assertThat(ret).isEmpty()
    }

    @Test
    fun `get will forget unrecoverable state`() {
        whenever(stateConvertor.toCordaSessionState(state, checkRevocation)).thenReturn(null)
        wrapper.get(keys)

        verify(sessionCache).forgetState(state)
    }

    @Test
    fun `get will send a re-establish request for unrecoverable state`() {
        whenever(stateConvertor.toCordaSessionState(state, checkRevocation)).thenReturn(null)
        wrapper.get(keys)

        verify(reEstablishmentMessageSender).send(state)
    }

    @Test
    fun `findStatesMatchingAny calls the state manager`() {
        wrapper.findStatesMatchingAny(filters)

        verify(stateManager).findByMetadataMatchingAny(filters)
    }

    @Test
    fun `findStatesMatchingAny calls the scheduler`() {
        wrapper.findStatesMatchingAny(filters)

        verify(sessionCache).validateStatesAndScheduleExpiry(keysToStates)
    }

    @Test
    fun `findStatesMatchingAny returns the correct value`() {
        val ret = wrapper.findStatesMatchingAny(filters)

        assertThat(ret).isEqualTo(
            mapOf(
                "key1" to StateManagerWrapper.StateManagerSessionState(
                    state,
                    sessionState,
                )
            )
        )
    }

    @Test
    fun `findStatesMatchingAny will not return unrecoverable state`() {
        whenever(stateConvertor.toCordaSessionState(state, checkRevocation)).thenReturn(null)
        val ret = wrapper.findStatesMatchingAny(filters)

        assertThat(ret).isEmpty()
    }

    @Test
    fun `findStatesMatchingAny will forget unrecoverable state`() {
        whenever(stateConvertor.toCordaSessionState(state, checkRevocation)).thenReturn(null)
        wrapper.findStatesMatchingAny(filters)

        verify(sessionCache).forgetState(state)
    }

    @Test
    fun `findStatesMatchingAny will send a re-establish request for unrecoverable state`() {
        whenever(stateConvertor.toCordaSessionState(state, checkRevocation)).thenReturn(null)
        wrapper.findStatesMatchingAny(filters)

        verify(reEstablishmentMessageSender).send(state)
    }

    @Test
    fun `upsert with update will set the beforeUpdate to true`() {
        val update = UpdateAction(state, false)
        wrapper.upsert(listOf(update))

        verify(sessionCache).validateStateAndScheduleExpiry(state, true)
    }

    @Test
    fun `upsert with create will set the beforeUpdate to false`() {
        val create = CreateAction(state)
        wrapper.upsert(listOf(create))

        verify(sessionCache).validateStateAndScheduleExpiry(state, false)
    }

    @Test
    fun `upsert with update will not update expired sessions`() {
        val update = UpdateAction(state, false)
        wrapper.upsert(listOf(update))

        verify(stateManager, never()).update(any())
    }

    @Test
    fun `upsert with update will update valid sessions`() {
        whenever(sessionCache.validateStateAndScheduleExpiry(state, true)).doReturn(state)
        val update = UpdateAction(state, false)
        wrapper.upsert(listOf(update))

        verify(stateManager).update(listOf(state))
    }

    @Test
    fun `upsert with create will not create expired sessions`() {
        val create = CreateAction(state)
        wrapper.upsert(listOf(create))

        verify(stateManager, never()).create(any())
    }

    @Test
    fun `upsert with create will create valid sessions`() {
        whenever(sessionCache.validateStateAndScheduleExpiry(state, false)).doReturn(state)
        val create = CreateAction(state)
        wrapper.upsert(listOf(create))

        verify(stateManager).create(listOf(state))
    }

    @Test
    fun `upsert with return the failures of the updates and creates`() {
        whenever(sessionCache.validateStateAndScheduleExpiry(any(), any())).doReturn(state)
        whenever(stateManager.create(any())).doReturn(setOf("key3", "key4"))
        whenever(stateManager.update(any())).doReturn(mapOf("key1" to state, "key2" to state))
        val update = UpdateAction(state, false)
        val create = CreateAction(state)

        val failed = wrapper.upsert(listOf(update, create))

        assertThat(failed)
            .containsEntry("key1", state)
            .containsEntry("key2", state)
            .containsEntry("key3", null)
            .containsEntry("key4", null)
            .hasSize(4)
    }
}
