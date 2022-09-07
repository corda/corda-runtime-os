package net.corda.flow.state

import co.paralleluniverse.common.util.Objects
import net.corda.data.KeyValuePairList
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.state.impl.FlowStackImpl
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.keyValuePairListOf
import net.corda.flow.utils.toMap
import org.assertj.core.api.Assertions.assertThat
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
        assertThat(flowStack.pop()!!.isRepresentationOf(flowStackItem3)).isTrue
        assertThat(flowStack.pop()!!.isRepresentationOf(flowStackItem2)).isTrue
        assertThat(flowStack.pop()!!.isRepresentationOf(flowStackItem1)).isTrue
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
        assertThat(flowStack.nearestFirst { it.isInitiatingFlow }!!.isRepresentationOf(flowStackItem3)).isTrue
    }

    @Test
    fun `peek returns null when the stack is empty`() {
        assertThat(FlowStackImpl(mutableListOf()).peek()).isNull()
    }

    @Test
    fun `peek returns the last element when the stack is not empty`() {
        assertThat(flowStack.peek()!!.isRepresentationOf(flowStackItem3)).isTrue
    }

    @Test
    fun `peekFirst returns null when the stack is empty`() {
        assertThat(FlowStackImpl(mutableListOf()).peekFirst()).isNull()
    }

    @Test
    fun `peekFirst returns the first element when the stack is not empty`() {
        assertThat(flowStack.peekFirst()!!.isRepresentationOf(flowStackItem1)).isTrue
    }

    @Test
    fun `pop returns null when the stack is empty`() {
        assertThat(FlowStackImpl(mutableListOf()).pop()).isNull()
    }

    @Test
    fun `pop returns the last inserted element when the stack is not empty`() {
        assertThat(flowStack.pop()!!.isRepresentationOf(flowStackItem3)).isTrue
        assertThat(flowStack.flowStackItems).hasSize(2)
    }
}

fun net.corda.flow.state.FlowStackItem.isRepresentationOf(other: FlowStackItem): Boolean {
    return Objects.equal(this.flowName, other.flowName) &&
            Objects.equal(this.sessionIds, other.sessionIds) &&
            Objects.equal(this.isInitiatingFlow, other.isInitiatingFlow) &&
            Objects.equal(this.contextUserProperties, other.contextUserProperties.toMap()) &&
            Objects.equal(this.contextPlatformProperties, other.contextPlatformProperties.toMap())
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


