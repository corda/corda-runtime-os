package net.corda.flow.state

import net.corda.data.KeyValuePairList
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.state.impl.FlowStackImpl
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.keyValuePairListOf
import net.corda.flow.utils.toMap
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FlowStackImplTest {
    lateinit var flowStack: FlowStackImpl
    private val flowStackItem1 = flowStackItem(isInitiatingFlow = true)
    private val flowStackItem2 = flowStackItem(
        sessionIds = mutableListOf("S1", "S2"),
        contextUserProperties = keyValuePairListOf(mapOf("user1" to "value1"))
    )
    private val flowStackItem3 = flowStackItem(
        sessionIds = mutableListOf("S1", "S2"),
        contextPlatformProperties = keyValuePairListOf(mapOf("context1" to "value1"))
    )

    @BeforeEach
    fun setup() {
        flowStack = FlowStackImpl(mutableListOf(flowStackItem1, flowStackItem2, flowStackItem3))
    }

    @Test
    fun `can be initialized from empty avro generated stack items`() {
        flowStack = FlowStackImpl(mutableListOf())
        assertThat(flowStack.flowStackItems).hasSize(0)
    }

    @Test
    fun `can be initialized from non empty avro generated stack items`() {
        assertThat(flowStack.flowStackItems).hasSize(3)
        FlowStackItemAssertions.assertThat(flowStack.pop()).isRepresentationOf(flowStackItem3)
        FlowStackItemAssertions.assertThat(flowStack.pop()).isRepresentationOf(flowStackItem2)
        FlowStackItemAssertions.assertThat(flowStack.pop()).isRepresentationOf(flowStackItem1)
    }

    @Test
    fun `original avro generated stack items can be retrieved`() {
        assertThat(flowStack.flowStackItems).hasSize(3)
        val avroItems = flowStack.toAvro()

        assertThat(avroItems).hasSize(3)
        assertThat(avroItems[0]).isEqualTo(flowStackItem1)
        assertThat(avroItems[1]).isEqualTo(flowStackItem2)
        assertThat(avroItems[2]).isEqualTo(flowStackItem3)
    }

    @Test
    fun `nearestFirst returns null when no items match`() {
        assertThat(flowStack.nearestFirst { !it.isInitiatingFlow }).isNull()
    }

    @Test
    fun `nearestFirst returns the most recently pushed matching element`() {
        FlowStackItemAssertions.assertThat(flowStack.nearestFirst { it.isInitiatingFlow })
            .isRepresentationOf(flowStackItem3)
    }

    @Test
    fun `peek returns null when the stack is empty`() {
        assertThat(FlowStackImpl(mutableListOf()).peek()).isNull()
    }

    @Test
    fun `peek returns the last element when the stack is not empty`() {
        FlowStackItemAssertions.assertThat(flowStack.peek()!!).isRepresentationOf(flowStackItem3)
    }

    @Test
    fun `peekFirst returns null when the stack is empty`() {
        assertThat(FlowStackImpl(mutableListOf()).peekFirst()).isNull()
    }

    @Test
    fun `peekFirst returns the first element when the stack is not empty`() {
        FlowStackItemAssertions.assertThat(flowStack.peekFirst()).isRepresentationOf(flowStackItem1)
    }

    @Test
    fun `pop returns null when the stack is empty`() {
        assertThat(FlowStackImpl(mutableListOf()).pop()).isNull()
    }

    @Test
    fun `pop returns the last inserted element when the stack is not empty`() {
        FlowStackItemAssertions.assertThat(flowStack.pop()).isRepresentationOf(flowStackItem3)
        assertThat(flowStack.flowStackItems).hasSize(2)
    }
}

fun flowStackItem(
    flowName: String = "flow-id",
    isInitiatingFlow: Boolean = true,
    sessionIds: MutableList<String> = mutableListOf(),
    contextUserProperties: KeyValuePairList = emptyKeyValuePairList(),
    contextPlatformProperties: KeyValuePairList = emptyKeyValuePairList()
) = FlowStackItem.newBuilder()
    .setFlowName(flowName)
    .setIsInitiatingFlow(isInitiatingFlow)
    .setSessionIds(sessionIds)
    .setContextUserProperties(contextUserProperties)
    .setContextPlatformProperties(contextPlatformProperties)
    .build()!!

/**
 * Custom assertion to verify that the non avro generated FlowStackItem instance is conceptually equal to the
 * Avro generated FlowStackItem instance.
 */
class FlowStackItemAssert(actual: net.corda.flow.state.FlowStackItem?) :
    AbstractObjectAssert<FlowStackItemAssert, net.corda.flow.state.FlowStackItem>(
        actual,
        FlowStackItemAssert::class.java
    ) {
    fun isRepresentationOf(avroFlowStackItem: FlowStackItem) {
        isNotNull
        assertThat(actual.flowName).isEqualTo(avroFlowStackItem.flowName)
        assertThat(actual.sessionIds).isEqualTo(avroFlowStackItem.sessionIds)
        assertThat(actual.isInitiatingFlow).isEqualTo(avroFlowStackItem.isInitiatingFlow)
        assertThat(actual.contextUserProperties).isEqualTo(avroFlowStackItem.contextUserProperties.toMap())
        assertThat(actual.contextPlatformProperties).isEqualTo(avroFlowStackItem.contextPlatformProperties.toMap())
    }
}

class FlowStackItemAssertions : Assertions() {
    companion object {
        fun assertThat(actual: net.corda.flow.state.FlowStackItem?) = FlowStackItemAssert(actual)
    }
}
