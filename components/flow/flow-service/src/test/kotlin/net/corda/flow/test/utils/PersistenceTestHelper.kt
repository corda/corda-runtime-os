package net.corda.flow.test.utils

import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.flow.persistence.manager.impl.PersistenceManagerImpl
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.state.impl.FlowCheckpointImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Get a mock instance of a PersistenceManager and a stubbed flowContext
 */
fun <R> mockPersistenceManagerAndStubContext(
    inputEventPayload: R, stubPersistenceState: PersistenceState? = null,
    config: SmartConfig = SmartConfigImpl.empty()
): Pair<PersistenceManagerImpl, FlowEventContext<R>> {
    val mockPersistenceManager = mock<PersistenceManagerImpl>()
    val flowContext = mockPersistenceStateInFlowContext(inputEventPayload, stubPersistenceState, config)
    return Pair(mockPersistenceManager, flowContext)
}

/**
 * Mock a checkpoint in a stubbed flow context and have it return the given [stubPersistenceState], [inputEventPayload] and [config]
 */
fun <R> mockPersistenceStateInFlowContext(
    inputEventPayload: R,
    stubPersistenceState: PersistenceState? = null,
    config: SmartConfig = SmartConfigImpl.empty()
): FlowEventContext<R> {
    val mockCheckpoint = mock<FlowCheckpointImpl>()
    val stubContext = buildFlowEventContext(mockCheckpoint, inputEventPayload, config)
    val holdingIdentity = HoldingIdentity(MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"), "group")
    whenever(mockCheckpoint.persistenceState).thenReturn(stubPersistenceState)
    whenever(mockCheckpoint.holdingIdentity).thenReturn(holdingIdentity)
    return stubContext
}

