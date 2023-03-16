package net.corda.simulator.runtime.messaging

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class SimFlowContextPropertiesTest {

    @Test
    fun `should put and get properties from flow context properties`(){
        // Given FlowContextProperties
        val contextProperties = SimFlowContextProperties(emptyMap())

        // When we put any key-value pair in it
        contextProperties.put("key-1", "val-1")

        // Then we should be able to fetch the value corresponding to the key
        assertEquals("val-1", contextProperties["key-1"])
    }


    @Test
    fun `should not allow modification in immutable flow context properties`(){
        // Given FlowContextProperties
        val contextProperties = SimFlowContextProperties(emptyMap())
        contextProperties.put("key-1", "val-1")

        // When we convert it into an immutable context property
        val immutableContextProperties  = contextProperties.toImmutableContext()

        // Then it should not allow new property insertion
        assertEquals("val-1", immutableContextProperties["key-1"])
        assertThrows<CordaRuntimeException>{
            immutableContextProperties.put("key-1", "val-2")
        }
    }
}