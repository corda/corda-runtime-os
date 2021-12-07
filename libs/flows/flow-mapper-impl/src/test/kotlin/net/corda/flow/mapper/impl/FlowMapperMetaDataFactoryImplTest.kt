package net.corda.flow.mapper.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class FlowMapperMetaDataFactoryImplTest {

    private val flowMapperMetaDataFactoryImpl = FlowMapperMetaDataFactoryImpl()
    private val p2pOut = "p2pOut"
    private val flowMapperEventTopic = "flowMapperEvent"
    private val flowEventTopic = "flowEvent"
    private val flowMapperTopics = FlowMapperTopics(p2pOut, flowMapperEventTopic, flowEventTopic)

    @Test
    fun startRPCFlow() {
        val key = "key"
        val holdingIdentity = HoldingIdentity("1", "2")
        val payload = StartRPCFlow("", "", "", holdingIdentity, Instant.now(), "")
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, payload)
        val meta = flowMapperMetaDataFactoryImpl.createFromEvent(
            flowMapperTopics, null, Record(
                flowMapperEventTopic, key, flowMapperEvent
            )
        )

        assertMetaData(meta, flowMapperEvent, key, flowEventTopic, holdingIdentity, payload, MessageDirection.INBOUND, null)
    }

    @Test
    fun sessionInitInbound() {
        val key = "key"
        val holdingIdentity = HoldingIdentity("1", "2")
        val payload = SessionEvent(1, 1, SessionInit("", "", null, holdingIdentity))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, payload)
        val meta = flowMapperMetaDataFactoryImpl.createFromEvent(
            flowMapperTopics, null, Record(
                flowMapperEventTopic, key, flowMapperEvent
            )
        )

        assertMetaData(meta, flowMapperEvent, key, flowEventTopic, holdingIdentity, payload, MessageDirection.INBOUND, null)
    }

    @Test
    fun sessionInitOutbound() {
        val key = "key"
        val holdingIdentity = HoldingIdentity("1", "2")
        val initiatedIdentity = HoldingIdentity("2", "2")
        val payload = SessionEvent(1, 1, SessionInit("", "", FlowKey("", holdingIdentity), initiatedIdentity))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.OUTBOUND, payload)
        val meta = flowMapperMetaDataFactoryImpl.createFromEvent(
            flowMapperTopics, null, Record(
                flowMapperEventTopic, key, flowMapperEvent
            )
        )

        assertMetaData(meta, flowMapperEvent, key, p2pOut, holdingIdentity, payload, MessageDirection.OUTBOUND, null)
    }

    @Test
    fun sessionEventInbound() {
        val key = "key"
        val payload = SessionEvent(1, 3, SessionData(null))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, payload)
        val meta = flowMapperMetaDataFactoryImpl.createFromEvent(
            flowMapperTopics, FlowMapperState(FlowKey(), null, FlowMapperStateType.OPEN), Record(
                flowMapperEventTopic, key, flowMapperEvent
            )
        )

        assertMetaData(meta, flowMapperEvent, key, flowEventTopic, null, payload, MessageDirection.INBOUND, null)
    }

    @Test
    fun sessionEventOutbound() {
        val key = "key"
        val payload = SessionEvent(1, 3, SessionData(null))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.OUTBOUND, payload)
        val meta = flowMapperMetaDataFactoryImpl.createFromEvent(
            flowMapperTopics, FlowMapperState(FlowKey(), null, FlowMapperStateType.OPEN), Record(
                flowMapperEventTopic, key, flowMapperEvent
            )
        )

        assertMetaData(meta, flowMapperEvent, key, p2pOut, null, payload, MessageDirection.OUTBOUND, null)
    }

    @Test
    fun scheduleCleanup() {
        val key = "key"
        val payload = ScheduleCleanup(Long.MAX_VALUE)
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, payload)
        val meta = flowMapperMetaDataFactoryImpl.createFromEvent(
            flowMapperTopics, FlowMapperState(FlowKey(), null, FlowMapperStateType.OPEN), Record(
                flowMapperEventTopic, key, flowMapperEvent
            )
        )

        assertMetaData(meta, flowMapperEvent, key, flowMapperEventTopic, null, payload, MessageDirection.INBOUND, Long.MAX_VALUE)
    }

    @Test
    fun executeCleanup() {
        val key = "key"
        val payload = ExecuteCleanup()
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, payload)
        val meta = flowMapperMetaDataFactoryImpl.createFromEvent(
            flowMapperTopics, FlowMapperState(FlowKey(), Long.MAX_VALUE, FlowMapperStateType.CLOSING), Record(
                flowMapperEventTopic, key, flowMapperEvent
            )
        )

        assertMetaData(meta, flowMapperEvent, key, null, null, payload, MessageDirection.INBOUND, Long.MAX_VALUE)
    }


    @Suppress("LongParameterList")
    private fun assertMetaData(
        metaData: FlowMapperMetaData,
        event: FlowMapperEvent,
        key: String,
        outputTopic: String?,
        holdingIdentity: HoldingIdentity?,
        payload: Any,
        messageDirection: MessageDirection?,
        expiryTime: Long?
    ) {
        assertThat(event).isEqualTo(metaData.flowMapperEvent)
        assertThat(key).isEqualTo(metaData.flowMapperEventKey)
        assertThat(holdingIdentity).isEqualTo(metaData.holdingIdentity)
        assertThat(payload).isEqualTo(metaData.payload)
        assertThat(messageDirection).isEqualTo(metaData.messageDirection)
        assertThat(expiryTime).isEqualTo(metaData.expiryTime)
        if (outputTopic == null) {
            assertThat(metaData.outputTopic).isNull()
        } else {
            assertThat(outputTopic).isEqualTo(metaData.outputTopic)
        }
    }
}