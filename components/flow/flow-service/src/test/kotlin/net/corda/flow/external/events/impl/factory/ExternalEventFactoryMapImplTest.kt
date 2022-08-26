package net.corda.flow.external.events.impl.factory

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.state.FlowCheckpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.service.component.ComponentContext

class ExternalEventFactoryMapImplTest {

    private val factoryA = FactoryA()
    private val factoryB = FactoryB()
    private val componentContext = mock<ComponentContext>()
    private lateinit var externalEventFactoryMap: ExternalEventFactoryMapImpl

    @BeforeEach
    fun beforeEach() {
        whenever(componentContext.locateServices(ExternalEventFactoryMapImpl.EXTERNAL_EVENT_HANDLERS))
            .thenReturn(arrayOf(factoryA, factoryB))
        externalEventFactoryMap = ExternalEventFactoryMapImpl(componentContext)
    }

    @Test
    fun `returns the factory matching the passed in factoryClassName`() {
        assertEquals(factoryA, externalEventFactoryMap.get(FactoryA::class.java.name))
        assertEquals(factoryB, externalEventFactoryMap.get(FactoryB::class.java.name))
    }

    @Test
    fun `throws an exception if no factory matches the passed in factoryClassName`() {
        assertThrows<FlowFatalException> { externalEventFactoryMap.get("this doesn't exist") }
    }

    private class FactoryA : ExternalEventFactory<String, String, String> {

        override val responseType: Class<String>
            get() = TODO("Not yet implemented")

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: String
        ): ExternalEventRecord {
            throw IllegalArgumentException("Not called")
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: String): String {
            throw IllegalArgumentException("Not called")
        }
    }

    private class FactoryB : ExternalEventFactory<ByteArray, ByteArray, ByteArray> {

        override val responseType: Class<ByteArray>
            get() = TODO("Not yet implemented")

        override fun createExternalEvent(
            checkpoint: FlowCheckpoint,
            flowExternalEventContext: ExternalEventContext,
            parameters: ByteArray
        ): ExternalEventRecord {
            throw IllegalArgumentException("Not called")
        }

        override fun resumeWith(checkpoint: FlowCheckpoint, response: ByteArray): ByteArray {
            throw IllegalArgumentException("Not called")
        }
    }
}