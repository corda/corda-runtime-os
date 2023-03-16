package net.corda.flow.state

import net.corda.flow.state.impl.FlatSerializableContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class FlatSerializableContextTest {
    val flow = mock<Flow>()

    val initialPlatformProperties = mapOf("p-key1" to "p-value1", "p-key2" to "p-value2")
    val initialUserPropertiesLevel = mapOf("u-key1" to "u-value1", "u-key2" to "u-value2")

    lateinit var flowContext: FlatSerializableContext

    @BeforeEach
    fun setup() {
        flowContext = FlatSerializableContext(
            contextUserProperties = initialUserPropertiesLevel,
            contextPlatformProperties = initialPlatformProperties
        )
    }

    @Test
    fun `user and platform get`() {
        assertThat(flowContext["key1"]).isNull()

        assertThat(flowContext["p-key1"]).isEqualTo("p-value1")
        assertThat(flowContext["p-key2"]).isEqualTo("p-value2")
        assertThat(flowContext["u-key1"]).isEqualTo("u-value1")
        assertThat(flowContext["u-key2"]).isEqualTo("u-value2")
    }

    @Test
    fun `flatten properties`() {
        val platformMap = flowContext.flattenPlatformProperties()
        val userMap = flowContext.flattenUserProperties()

        assertThat(userMap.size).isEqualTo(2)

        assertThat(userMap["u-key1"]).isEqualTo("u-value1")
        assertThat(userMap["u-key2"]).isEqualTo("u-value2")

        assertThat(platformMap.size).isEqualTo(2)

        assertThat(platformMap["p-key1"]).isEqualTo("p-value1")
        assertThat(platformMap["p-key2"]).isEqualTo("p-value2")
    }

    @Test
    fun `setting values throws`() {
        assertThrows<CordaRuntimeException> { flowContext.put("key", "value") }
        assertThrows<CordaRuntimeException> { flowContext.platformProperties["key"] = "value" }
    }
}
