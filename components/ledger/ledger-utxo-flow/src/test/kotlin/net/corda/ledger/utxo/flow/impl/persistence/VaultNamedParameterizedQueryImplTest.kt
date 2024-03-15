package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.application.services.FlowCheckpointService
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.flow.persistence.query.StableResultSetExecutor
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.data.transaction.UtxoLedgerLastPersistedTimestamp
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ALICE_X500_HOLDING_IDENTITY
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryExternalEventFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.utilities.time.Clock
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PagedQuery.ResultSet
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class VaultNamedParameterizedQueryImplTest {

    private companion object {
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda_TimestampLimit"
        val customTimestamp: Instant = Instant.ofEpochSecond(1200)
        val now: Instant = Instant.ofEpochSecond(3600)
        val future: Instant = now.plusSeconds(2)
        val past: Instant = now.minusSeconds(1)
        val results = listOf("A", "B")


        @JvmStatic
        private fun getTimeArguments() : Array<TimeArguments> = arrayOf(
            TimeArguments(null, now),
            TimeArguments(now, now),
            TimeArguments(past, now),
            TimeArguments(future, future)
        )

    }

    data class TimeArguments(val checkpointValue: Instant?, val expectedValue: Instant)

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val resultSetFactory = mock<ResultSetFactory>()
    private val resultSet = mock<ResultSet<Any>>()
    private val clock = mock<Clock>()
    private val resultSetExecutorCaptor = argumentCaptor<StableResultSetExecutor<Any>>()
    private val mapCaptor = argumentCaptor<Map<String, Any>>()
    private val flowCheckpointService = mock<FlowCheckpointService>()
    private val flowCheckpoint = mock<FlowCheckpoint>()

    private val query = VaultNamedParameterizedQueryImpl(
        externalEventExecutor = externalEventExecutor,
        currentSandboxGroupContext = currentSandboxGroupContext,
        resultSetFactory = resultSetFactory,
        parameters = mutableMapOf(),
        queryName = "",
        limit = 1,
        offset = 0,
        resultClass = Any::class.java,
        clock = clock,
        flowCheckpointService = flowCheckpointService
    )

    @BeforeEach
    fun beforeEach() {
        whenever(resultSetFactory.create(mapCaptor.capture(), any(), any(), resultSetExecutorCaptor.capture())).thenReturn(resultSet)
        whenever(resultSet.next()).thenReturn(results)
        whenever(clock.instant()).thenReturn(now)
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
        whenever(flowCheckpointService.getCheckpoint()).thenReturn(flowCheckpoint)
        whenever(flowCheckpoint.readCustomState(UtxoLedgerLastPersistedTimestamp::class.java))
            .thenReturn(null)
    }

    @Test
    fun `setLimit updates the limit`() {
        query.execute()
        verify(resultSetFactory).create(any(), eq(1), any<Class<Any>>(), any())

        query.setLimit(10)
        query.execute()
        verify(resultSetFactory).create(any(), eq(10), any<Class<Any>>(), any())
    }

    @Test
    fun `setOffset is not supported`() {
        assertThatThrownBy { query.setOffset(10) }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `setLimit cannot be negative`() {
        assertThatThrownBy { query.setLimit(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `setLimit cannot be zero`() {
        assertThatThrownBy { query.setLimit(0) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `setting the timestamp limit adds it to the parameters`() {
        val parameterNameOne = "one"
        val parameterOne = "param one"
        query.setParameter(parameterNameOne, parameterOne)
        query.setCreatedTimestampLimit(customTimestamp)

        query.execute()
        verify(flowCheckpointService, never()).getCheckpoint()
        assertThat(mapCaptor.firstValue).containsAllEntriesOf(mapOf(parameterNameOne to parameterOne, TIMESTAMP_LIMIT_PARAM_NAME to customTimestamp))
    }

    @ParameterizedTest
    @MethodSource("getTimeArguments")
    fun `execute sets the timestamp limit to now or the future if in the context if not set when there are no other parameters`(args: TimeArguments) {
        whenever(flowCheckpoint.readCustomState(UtxoLedgerLastPersistedTimestamp::class.java))
            .thenReturn(args.checkpointValue?.let{UtxoLedgerLastPersistedTimestamp(it)})
        query.execute()
        verify(clock).instant()
        assertThat(mapCaptor.firstValue).containsExactlyEntriesOf(mapOf(TIMESTAMP_LIMIT_PARAM_NAME to args.expectedValue))
    }



    @ParameterizedTest
    @MethodSource("getTimeArguments")
    fun `execute sets the timestamp limit to now  or the future if in the context if not set when there are other parameters`(args: TimeArguments) {
        whenever(flowCheckpoint.readCustomState(UtxoLedgerLastPersistedTimestamp::class.java))
            .thenReturn(args.checkpointValue?.let{UtxoLedgerLastPersistedTimestamp(it)})
        val parameterNameOne = "one"
        val parameterOne = "param one"
        query.setParameter(parameterNameOne, parameterOne)
        query.execute()
        verify(clock).instant()
        assertThat(mapCaptor.firstValue).containsExactlyEntriesOf(
            mapOf(
                parameterNameOne to parameterOne,
                TIMESTAMP_LIMIT_PARAM_NAME to args.expectedValue
            )
        )
    }

    @Test
    fun `setParameter sets a parameter`() {
        val parameterNameOne = "one"
        val parameterNameTwo = "two"
        val parameterOne = "param one"
        val parameterTwo = "param two"
        query.setParameter(parameterNameOne, parameterOne)
        query.setParameter(parameterNameTwo, parameterTwo)

        query.execute()
        assertThat(mapCaptor.firstValue).containsAllEntriesOf(mapOf(parameterNameOne to parameterOne, parameterNameTwo to parameterTwo))
    }

    @Test
    fun `setParameters overwrites all parameters`() {
        val parameterNameOne = "one"
        val parameterNameTwo = "two"
        val parameterNameThree = "three"
        val parameterOne = "param one"
        val parameterTwo = "param two"
        val parameterThree = "param three"
        val newParameters = mapOf(parameterNameTwo to parameterTwo, parameterNameThree to parameterThree)

        query.setParameter(parameterNameOne, parameterOne)
        query.setParameters(newParameters)

        query.execute()
        assertThat(mapCaptor.firstValue).containsAllEntriesOf(newParameters)
    }

    @Test
    fun `execute creates a result set, gets the next page and returns the result set`() {
        assertThat(query.execute()).isEqualTo(resultSet)
        verify(resultSetFactory).create(any(), any(), any<Class<Any>>(), any())
        verify(resultSet).next()
    }

    @Test
    fun `rethrows CordaRuntimeExceptions as CordaPersistenceExceptions`() {
        whenever(externalEventExecutor.execute(any<Class<VaultNamedQueryExternalEventFactory>>(), any()))
            .thenThrow(CordaRuntimeException("boom"))

        query.execute()

        val resultSetExecutor = resultSetExecutorCaptor.firstValue
        assertThatThrownBy { resultSetExecutor.execute(emptyMap(), null, null) }.isInstanceOf(CordaPersistenceException::class.java)
    }

    @Test
    fun `does not rethrow general exceptions as CordaPersistenceExceptions`() {
        whenever(externalEventExecutor.execute(any<Class<VaultNamedQueryExternalEventFactory>>(), any()))
            .thenThrow(IllegalStateException("boom"))

        query.execute()

        val resultSetExecutor = resultSetExecutorCaptor.firstValue
        assertThatThrownBy { resultSetExecutor.execute(emptyMap(), null, null) }.isInstanceOf(IllegalStateException::class.java)
    }
}
