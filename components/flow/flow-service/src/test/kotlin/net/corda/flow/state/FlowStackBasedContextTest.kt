package net.corda.flow.state

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.flow.state.impl.FlowStackBasedContext
import net.corda.flow.state.impl.FlowStackImpl
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.v5.application.flows.Flow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class FlowStackBasedContextTest {
    val flow = mock<Flow>()

    val platformPropertiesLevel1 = KeyValueStore().apply {
        this["p-key1"] = "p-value1"
        this["p-key2"] = "p-value2"
    }

    val platformPropertiesLevel2 = KeyValueStore().apply {
        this["p-key3"] = "p-value3"
        this["p-key2"] = "p-value2-overwritten"
    }

    val userPropertiesLevel1 = KeyValueStore().apply {
        this["u-key1"] = "u-value1"
        this["u-key2"] = "u-value2"
    }

    val userPropertiesLevel2 = KeyValueStore().apply {
        this["u-key3"] = "u-value3"
        this["u-key2"] = "u-value2-overwritten"
    }

    lateinit var flowStack: FlowStackImpl
    lateinit var flowContext: FlowStackBasedContext

    @BeforeEach
    fun setup() {
        flowStack = FlowStackImpl(mutableListOf())
        flowContext = FlowStackBasedContext(flowStack)
    }

    @Test
    fun `simple user context put and get`() {
        flowStack.push(flow)

        assertThat(flowContext["key1"]).isNull()

        flowContext.put("key1", "value1")
        flowContext.put("key2", "value2")

        assertThat(flowContext["key1"]).isEqualTo("value1")
        assertThat(flowContext["key2"]).isEqualTo("value2")

        flowContext.put("key1", "value1-overwritten")

        assertThat(flowContext["key1"]).isEqualTo("value1-overwritten")
        assertThat(flowContext["key2"]).isEqualTo("value2")
    }

    @Test
    fun `user context put and get with initial properties`() {
        val copyOfPropertiesList = mutableListOf<KeyValuePair>().apply {
            addAll(userPropertiesLevel1.avro.items)
        }

        val modifiedUserPropertiesLevel1 = KeyValuePairList(copyOfPropertiesList).apply {
            // Note that the api would have thrown trying to add this property in the real world, but it's worth a test
            this.items.add(KeyValuePair("p-key1", "ignored in favour of platform property"))
        }

        flowStack.pushWithContext(
            flow,
            contextUserProperties = modifiedUserPropertiesLevel1,
            contextPlatformProperties = platformPropertiesLevel1.avro
        )

        flowContext.put("key1", "value1")

        flowStack.pushWithContext(
            flow,
            contextUserProperties = userPropertiesLevel2.avro,
            contextPlatformProperties = platformPropertiesLevel2.avro
        )

        assertThat(flowContext["p-key1"]).isEqualTo("p-value1")
        assertThat(flowContext["p-key2"]).isEqualTo("p-value2-overwritten")
        assertThat(flowContext["p-key3"]).isEqualTo("p-value3")
        assertThat(flowContext["u-key1"]).isEqualTo("u-value1")
        assertThat(flowContext["u-key2"]).isEqualTo("u-value2-overwritten")
        assertThat(flowContext["u-key3"]).isEqualTo("u-value3")

        flowContext.put("key2", "value2")
        flowContext.put("u-key1", "u-value1-overwritten")

        assertThat(flowContext["key1"]).isEqualTo("value1")
        assertThat(flowContext["key2"]).isEqualTo("value2")
        assertThat(flowContext["p-key1"]).isEqualTo("p-value1")
        assertThat(flowContext["p-key2"]).isEqualTo("p-value2-overwritten")
        assertThat(flowContext["p-key3"]).isEqualTo("p-value3")
        assertThat(flowContext["u-key1"]).isEqualTo("u-value1-overwritten")
        assertThat(flowContext["u-key2"]).isEqualTo("u-value2-overwritten")
        assertThat(flowContext["u-key3"]).isEqualTo("u-value3")
    }

    @Test
    fun `simple platform context put and get`() {
        flowStack.push(flow)

        assertThat(flowContext["key1"]).isNull()

        flowContext.platformProperties["key1"] = "value1"
        flowContext.platformProperties["key2"] = "value2"

        assertThat(flowContext["key1"]).isEqualTo("value1")
        assertThat(flowContext["key2"]).isEqualTo("value2")
    }

    @Test
    fun `put platform properties with initial properties`() {
        flowStack.pushWithContext(
            flow,
            contextUserProperties = userPropertiesLevel1.avro,
            contextPlatformProperties = platformPropertiesLevel1.avro
        )

        flowContext.platformProperties["key1"] = "value1"

        flowStack.pushWithContext(
            flow,
            contextUserProperties = userPropertiesLevel2.avro,
            contextPlatformProperties = platformPropertiesLevel2.avro
        )

        flowContext.platformProperties["key2"] = "value2"

        assertThat(flowContext["key1"]).isEqualTo("value1")
        assertThat(flowContext["key2"]).isEqualTo("value2")
        assertThat(flowContext["p-key1"]).isEqualTo("p-value1")
        assertThat(flowContext["p-key2"]).isEqualTo("p-value2-overwritten")
        assertThat(flowContext["p-key3"]).isEqualTo("p-value3")
        assertThat(flowContext["u-key1"]).isEqualTo("u-value1")
        assertThat(flowContext["u-key2"]).isEqualTo("u-value2-overwritten")
        assertThat(flowContext["u-key3"]).isEqualTo("u-value3")
    }

    @Test
    fun `user writing over platform property throws`() {
        flowStack.pushWithContext(
            flow,
            contextUserProperties = emptyKeyValuePairList(),
            contextPlatformProperties = platformPropertiesLevel1.avro
        )

        assertThrows<IllegalArgumentException> { flowContext.put("p-key1", "value") }
    }

    @Test
    fun `using reserved corda prefix for user property throws`() {
        flowStack.pushWithContext(
            flow,
            contextUserProperties = emptyKeyValuePairList(),
            contextPlatformProperties = platformPropertiesLevel1.avro
        )

        assertThrows<IllegalArgumentException> { flowContext.put("corda.property", "value") }
        assertThrows<IllegalArgumentException> { flowContext.put("CORDA.property", "value") }
    }

    @Test
    fun `platform writing over platform property throws`() {
        flowStack.pushWithContext(
            flow,
            contextUserProperties = emptyKeyValuePairList(),
            contextPlatformProperties = platformPropertiesLevel1.avro
        )

        assertThrows<IllegalArgumentException> { flowContext.platformProperties["p-key1"] = "value" }
    }

    @Test
    fun `flatten properties`() {
        flowStack.pushWithContext(
            flow,
            contextUserProperties = userPropertiesLevel1.avro,
            contextPlatformProperties = platformPropertiesLevel1.avro
        )

        flowContext.put("userkey1", "uservalue1")
        flowContext.platformProperties["platformkey1"] = "platformvalue1"

        flowStack.pushWithContext(
            flow,
            contextUserProperties = userPropertiesLevel2.avro,
            contextPlatformProperties = platformPropertiesLevel2.avro
        )

        flowContext.put("userkey2", "uservalue2")
        flowContext.platformProperties["platformkey2"] = "platformvalue2"
        flowContext.put("u-key1", "u-value1-overwritten-by-context-api")

        val platformMap = flowContext.flattenPlatformProperties()
        val userMap = flowContext.flattenUserProperties()

        assertThat(userMap.size).isEqualTo(5)

        assertThat(userMap["userkey1"]).isEqualTo("uservalue1")
        assertThat(userMap["userkey2"]).isEqualTo("uservalue2")
        assertThat(userMap["u-key1"]).isEqualTo("u-value1-overwritten-by-context-api")
        assertThat(userMap["u-key2"]).isEqualTo("u-value2-overwritten")
        assertThat(userMap["u-key3"]).isEqualTo("u-value3")

        assertThat(platformMap.size).isEqualTo(5)

        assertThat(platformMap["platformkey1"]).isEqualTo("platformvalue1")
        assertThat(platformMap["platformkey2"]).isEqualTo("platformvalue2")
        assertThat(platformMap["p-key1"]).isEqualTo("p-value1")
        assertThat(platformMap["p-key2"]).isEqualTo("p-value2-overwritten")
        assertThat(platformMap["p-key3"]).isEqualTo("p-value3")
    }

    @Test
    fun `unwind flow stack`() {
        flowStack.pushWithContext(
            flow,
            contextUserProperties = userPropertiesLevel1.avro,
            contextPlatformProperties = platformPropertiesLevel1.avro
        )

        flowContext.put("userkey1", "uservalue1")
        flowContext.platformProperties["platformkey1"] = "platformvalue1"

        flowStack.pushWithContext(
            flow,
            contextUserProperties = userPropertiesLevel2.avro,
            contextPlatformProperties = platformPropertiesLevel2.avro
        )

        flowContext.put("userkey2", "uservalue2")
        flowContext.platformProperties["platformkey2"] = "platformvalue2"

        flowStack.pop()

        assertThat(flowContext["p-key1"]).isEqualTo("p-value1")
        assertThat(flowContext["u-key1"]).isEqualTo("u-value1")
        // Non overwritten values
        assertThat(flowContext["p-key2"]).isEqualTo("p-value2")
        assertThat(flowContext["u-key2"]).isEqualTo("u-value2")
        // Popped entirely
        assertThat(flowContext["u-key3"]).isNull()
        assertThat(flowContext["p-key3"]).isNull()

        assertThat(flowContext["userkey1"]).isEqualTo("uservalue1")
        assertThat(flowContext["platformkey1"]).isEqualTo("platformvalue1")
        // Popped
        assertThat(flowContext["userkey2"]).isNull()
        assertThat(flowContext["platformkey2"]).isNull()
    }
}