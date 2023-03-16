package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.factory.impl.FlowRecordFactoryImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowRecordFactoryImplTest {

    @Test
    fun `create flow event record`() {
        val expected = Record(FLOW_EVENT_TOPIC, "flowId", FlowEvent("flowId", 3))
        assertThat(FlowRecordFactoryImpl().createFlowEventRecord("flowId", 3)).isEqualTo(expected)
    }

    @Test
    fun `create flow status record`() {
        val status = FlowStatus().apply { key = FlowKey("id", HoldingIdentity()) }
        val expected = Record(FLOW_STATUS_TOPIC, status.key, status)
        assertThat(FlowRecordFactoryImpl().createFlowStatusRecord(status)).isEqualTo(expected)
    }

    @Test
    fun `create flow mapper event record with session event`() {
        val sessionEvent = SessionEvent().apply { sessionId = "id1" }
        val expected = Record(FLOW_MAPPER_EVENT_TOPIC, sessionEvent.sessionId, FlowMapperEvent(sessionEvent))
        assertThat(FlowRecordFactoryImpl().createFlowMapperEventRecord(sessionEvent.sessionId, sessionEvent)).isEqualTo(expected)
    }

    @Test
    fun `create flow mapper event record with schedule cleanup event`() {
        val cleanup = ScheduleCleanup(1000)
        val expected = Record(FLOW_MAPPER_EVENT_TOPIC, "flowKey.toString", FlowMapperEvent(cleanup))
        assertThat(FlowRecordFactoryImpl().createFlowMapperEventRecord("flowKey.toString", cleanup)).isEqualTo(expected)
    }
}

