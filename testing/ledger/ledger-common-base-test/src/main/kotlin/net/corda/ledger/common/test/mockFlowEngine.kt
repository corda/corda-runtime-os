package net.corda.ledger.common.test

import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowContextPropertyKeys
import net.corda.v5.application.flows.FlowEngine
import org.mockito.Mockito

internal fun mockFlowEngine(): FlowEngine {

    val flowEngine = Mockito.mock(FlowEngine::class.java)
    val flowContextProperties = Mockito.mock(FlowContextProperties::class.java)
    Mockito.`when`(flowContextProperties.get(FlowContextPropertyKeys.CPI_NAME)).thenReturn("CPI name")
    Mockito.`when`(flowContextProperties.get(FlowContextPropertyKeys.CPI_VERSION)).thenReturn("CPI version")
    Mockito.`when`(flowContextProperties.get(FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH))
        .thenReturn(SecureHashImpl("SHA-256", "Fake-value".toByteArray()).toHexString())
    Mockito.`when`(flowContextProperties.get(FlowContextPropertyKeys.CPI_FILE_CHECKSUM))
        .thenReturn(SecureHashImpl("SHA-256", "Another-Fake-value".toByteArray()).toHexString())
    Mockito.`when`(flowEngine.flowContextProperties).thenReturn(flowContextProperties)

    return flowEngine
}
