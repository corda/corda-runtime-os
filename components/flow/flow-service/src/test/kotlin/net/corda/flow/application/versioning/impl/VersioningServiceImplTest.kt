package net.corda.flow.application.versioning.impl

import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowContext
import net.corda.v5.application.flows.FlowEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class VersioningServiceImplTest {

    private val flowEngine = mock<FlowEngine>()
    private val flowContextProperties = mock<FlowContext>()
    private val platformProperties = mock<ContextPlatformProperties>()
    private val versioningService = VersioningServiceImpl(flowEngine)

    @BeforeEach
    fun beforeEach() {
        whenever(flowEngine.flowContextProperties).thenReturn(flowContextProperties)
        whenever(flowContextProperties.platformProperties).thenReturn(platformProperties)
    }

    @Test
    fun `versionedSubFlow with a VersionedSendFlowFactory executes a VersioningFlow`() {
        versioningService.versionedSubFlow(mock<VersionedSendFlowFactory<*>>(), emptyList())
        verify(flowEngine).subFlow(any<VersioningFlow<*>>())
    }

    @Test
    fun `versionedSubFlow with a VersionedReceiveFlowFactory executes a ReceiveVersioningFlow`() {
        versioningService.versionedSubFlow(mock<VersionedReceiveFlowFactory<*>>(), mock())
        verify(flowEngine).subFlow(any<ReceiveVersioningFlow<*>>())
    }

    @Test
    fun `peekCurrentVersioning returns the current versioning information in the flow context properties`() {
        whenever(flowContextProperties.get(VERSIONING_PROPERTY_NAME)).thenReturn(3.toString())
        assertThat(versioningService.peekCurrentVersioning()).isEqualTo(3 to linkedMapOf<String, Any>())
    }

    @Test
    fun `peekCurrentVersioning returns null when there is no current versioning information in the flow context properties`() {
        whenever(flowContextProperties.get(VERSIONING_PROPERTY_NAME)).thenReturn(null)
        assertThat(versioningService.peekCurrentVersioning()).isNull()
    }

    @Test
    fun `peekCurrentVersioning returns null when the current versioning value in the flow context properties is RESET_VERSIONINIG_MARKER`() {
        whenever(flowContextProperties.get(VERSIONING_PROPERTY_NAME)).thenReturn(RESET_VERSIONING_MARKER)
        assertThat(versioningService.peekCurrentVersioning()).isNull()
    }

    @Test
    fun `setCurrentVersioning stores the version in the flow context properties`() {
        versioningService.setCurrentVersioning(1)
        verify(platformProperties)[VERSIONING_PROPERTY_NAME] = 1.toString()
    }

    @Test
    fun `resetCurrentVersioning sets the current versioning value in the flow context properties to RESET_VERSIONING_MARKER if the property already existed`() {
        whenever(flowContextProperties.get(VERSIONING_PROPERTY_NAME)).thenReturn(1.toString())
        versioningService.resetCurrentVersioning()
        verify(platformProperties)[VERSIONING_PROPERTY_NAME] = RESET_VERSIONING_MARKER
    }

    @Test
    fun `resetCurrentVersioning does not set the current versioning value in the flow context properties if the property does not exist`() {
        whenever(flowContextProperties.get(VERSIONING_PROPERTY_NAME)).thenReturn(null)
        versioningService.resetCurrentVersioning()
        verify(platformProperties, never()).put(eq(VERSIONING_PROPERTY_NAME), any())
    }
}