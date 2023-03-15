package net.corda.flow.state

import net.corda.flow.state.impl.FlatSerializableContext
import net.corda.flow.state.impl.MutableFlatSerializableContext
import net.corda.v5.application.flows.Flow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class MutableFlatSerializableContextTest {
    val flow = mock<Flow>()

    val initialPlatformProperties = mapOf("p-key1" to "p-value1", "p-key2" to "p-value2")
    val initialUserPropertiesLevel = mapOf("u-key1" to "u-value1", "u-key2" to "u-value2")

    lateinit var flowContext: FlatSerializableContext

    @BeforeEach
    fun setup() {
        flowContext = MutableFlatSerializableContext(
            contextUserProperties = initialUserPropertiesLevel,
            contextPlatformProperties = initialPlatformProperties
        )
    }

    @Test
    fun `simple user context put and get`() {
        assertThat(flowContext["key1"]).isNull()

        flowContext.put("key1", "value1")
        flowContext.put("key2", "value2")

        assertThat(flowContext["key1"]).isEqualTo("value1")
        assertThat(flowContext["key2"]).isEqualTo("value2")

        flowContext.put("key1", "value1-overwritten")

        assertThat(flowContext["key1"]).isEqualTo("value1-overwritten")
        assertThat(flowContext["key2"]).isEqualTo("value2")

        assertThat(flowContext["p-key1"]).isEqualTo("p-value1")
        assertThat(flowContext["p-key2"]).isEqualTo("p-value2")
        assertThat(flowContext["u-key1"]).isEqualTo("u-value1")
        assertThat(flowContext["u-key2"]).isEqualTo("u-value2")
    }

    @Test
    fun `simple platform context put and get`() {
        assertThat(flowContext["key1"]).isNull()

        flowContext.platformProperties["key1"] = "value1"
        flowContext.platformProperties["key2"] = "value2"

        assertThat(flowContext["key1"]).isEqualTo("value1")
        assertThat(flowContext["key2"]).isEqualTo("value2")
    }

    @Test
    fun `user writing over platform property throws`() {
        assertThrows<IllegalArgumentException> { flowContext.put("p-key1", "value") }
    }

    @Test
    fun `using reserved corda prefix for user property throws`() {
        assertThrows<IllegalArgumentException> { flowContext.put("corda.property", "value") }
        assertThrows<IllegalArgumentException> { flowContext.put("CORDA.property", "value") }
    }

    @Test
    fun `platform writing over platform property does not throw`() {
        assertDoesNotThrow { flowContext.platformProperties["p-key1"] = "value" }
    }

    @Test
    fun `flatten properties`() {
        flowContext.put("userkey1", "uservalue1")
        flowContext.platformProperties["platformkey1"] = "platformvalue1"
        flowContext.put("userkey2", "uservalue2")
        flowContext.platformProperties["platformkey2"] = "platformvalue2"
        flowContext.put("u-key1", "u-value1-overwritten-by-context-api")

        val platformMap = flowContext.flattenPlatformProperties()
        val userMap = flowContext.flattenUserProperties()

        assertThat(userMap.size).isEqualTo(4)

        assertThat(userMap["userkey1"]).isEqualTo("uservalue1")
        assertThat(userMap["userkey2"]).isEqualTo("uservalue2")
        assertThat(userMap["u-key1"]).isEqualTo("u-value1-overwritten-by-context-api")
        assertThat(userMap["u-key2"]).isEqualTo("u-value2")

        assertThat(platformMap.size).isEqualTo(4)

        assertThat(platformMap["platformkey1"]).isEqualTo("platformvalue1")
        assertThat(platformMap["platformkey2"]).isEqualTo("platformvalue2")
        assertThat(platformMap["p-key1"]).isEqualTo("p-value1")
        assertThat(platformMap["p-key2"]).isEqualTo("p-value2")
    }
}
