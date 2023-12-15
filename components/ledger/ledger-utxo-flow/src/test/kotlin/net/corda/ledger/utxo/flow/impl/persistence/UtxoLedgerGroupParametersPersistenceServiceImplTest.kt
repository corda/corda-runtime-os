package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ALICE_X500_HOLDING_IDENTITY
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedGroupParametersExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistSignedGroupParametersIfDoNotExistExternalEventFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UtxoLedgerGroupParametersPersistenceServiceImplTest {

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val groupParametersCache = mock<GroupParametersCache>()
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()

    private val utxoLedgerGroupParametersPersistenceService = UtxoLedgerGroupParametersPersistenceServiceImpl(
        currentSandboxGroupContext,
        externalEventExecutor,
        groupParametersCache
    )

    @BeforeEach
    fun beforeEach() {
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
    }

    @Test
    fun `find group parameters that are not cached adds them to the cache and returns them`() {
        val signedGroupParameters = mock<SignedGroupParameters>()
        whenever(groupParametersCache.get(any())).thenReturn(null)
        whenever(externalEventExecutor.execute(any<Class<FindSignedGroupParametersExternalEventFactory>>(), any()))
            .thenReturn(listOf(signedGroupParameters))
        assertThat(utxoLedgerGroupParametersPersistenceService.find(mock())).isEqualTo(signedGroupParameters)
        verify(groupParametersCache).put(signedGroupParameters)
    }

    @Test
    fun `find group parameters that are not cached and does not exist returns null and does not cache them`() {
        whenever(groupParametersCache.get(any())).thenReturn(null)
        whenever(externalEventExecutor.execute(any<Class<FindSignedGroupParametersExternalEventFactory>>(), any())).thenReturn(emptyList())
        assertThat(utxoLedgerGroupParametersPersistenceService.find(mock())).isEqualTo(null)
        verify(groupParametersCache, never()).put(any())
    }

    @Test
    fun `find group parameters that throws exception from external event rethrows persistence exception`() {
        whenever(groupParametersCache.get(any())).thenReturn(null)
        whenever(externalEventExecutor.execute(any<Class<FindSignedGroupParametersExternalEventFactory>>(), any()))
            .thenThrow(CordaRuntimeException(""))
        assertThatThrownBy { utxoLedgerGroupParametersPersistenceService.find(mock()) }
            .isExactlyInstanceOf(CordaPersistenceException::class.java)
        verify(groupParametersCache, never()).put(any())
    }

    @Test
    fun `find group parameters that are cached does not execute external event`() {
        val signedGroupParameters = mock<SignedGroupParameters>()
        whenever(groupParametersCache.get(any())).thenReturn(signedGroupParameters)
        assertThat(utxoLedgerGroupParametersPersistenceService.find(mock())).isEqualTo(signedGroupParameters)
        verify(externalEventExecutor, never()).execute(any<Class<FindSignedGroupParametersExternalEventFactory>>(), any())
        verify(groupParametersCache, never()).put(signedGroupParameters)
    }

    @Test
    fun `persist group parameters that are not cached adds them to the cache`() {
        val signedGroupParameters = mock<SignedGroupParameters>()
        whenever(signedGroupParameters.hash).thenReturn(mock())
        whenever(signedGroupParameters.groupParameters).thenReturn(byteArrayOf(1))
        whenever(signedGroupParameters.mgmSignature).thenReturn(DigitalSignatureWithKey(mock(), byteArrayOf(2)))
        whenever(signedGroupParameters.mgmSignatureSpec).thenReturn(mock())
        whenever(groupParametersCache.get(any())).thenReturn(null)
        utxoLedgerGroupParametersPersistenceService.persistIfDoesNotExist(signedGroupParameters)
        verify(externalEventExecutor).execute(any<Class<PersistSignedGroupParametersIfDoNotExistExternalEventFactory>>(), any())
        verify(groupParametersCache).put(signedGroupParameters)
    }

    @Test
    fun `persist group parameters that throws exception from external event rethrows as persistence exception`() {
        val signedGroupParameters = mock<SignedGroupParameters>()
        whenever(signedGroupParameters.hash).thenReturn(mock())
        whenever(signedGroupParameters.groupParameters).thenReturn(byteArrayOf(1))
        whenever(signedGroupParameters.mgmSignature).thenReturn(DigitalSignatureWithKey(mock(), byteArrayOf(2)))
        whenever(signedGroupParameters.mgmSignatureSpec).thenReturn(mock())
        whenever(groupParametersCache.get(any())).thenReturn(null)
        whenever(externalEventExecutor.execute(any<Class<PersistSignedGroupParametersIfDoNotExistExternalEventFactory>>(), any()))
            .thenThrow(CordaRuntimeException(""))
        assertThatThrownBy { utxoLedgerGroupParametersPersistenceService.persistIfDoesNotExist(signedGroupParameters) }
            .isExactlyInstanceOf(CordaPersistenceException::class.java)
        verify(groupParametersCache, never()).put(signedGroupParameters)
    }

    @Test
    fun `persist group parameters that are cached does not execute external event`() {
        val signedGroupParameters = mock<SignedGroupParameters>()
        whenever(signedGroupParameters.hash).thenReturn(mock())
        whenever(groupParametersCache.get(any())).thenReturn(signedGroupParameters)
        utxoLedgerGroupParametersPersistenceService.persistIfDoesNotExist(signedGroupParameters)
        verify(externalEventExecutor, never()).execute(any<Class<PersistSignedGroupParametersIfDoNotExistExternalEventFactory>>(), any())
        verify(groupParametersCache, never()).put(signedGroupParameters)
    }
}
