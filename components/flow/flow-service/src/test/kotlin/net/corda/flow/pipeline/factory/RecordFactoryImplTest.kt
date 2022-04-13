package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.factory.impl.RecordFactoryImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecordFactoryImplTest {

    @Test
    fun `create flow event record`(){
        val expected = Record(FLOW_EVENT_TOPIC,"flowId", FlowEvent("flowId",3))
        assertThat(RecordFactoryImpl().createFlowEventRecord("flowId",3)).isEqualTo(expected)
    }

    @Test
    fun `create flow status record`(){
        val status = FlowStatus().apply { key = FlowKey("id", HoldingIdentity())}
        val expected = Record(FLOW_STATUS_TOPIC, status.key, status)
        assertThat(RecordFactoryImpl().createFlowStatusRecord(status)).isEqualTo(expected)
    }

    @Test
    fun `create flow mapper session event record`(){
        val sessionEvent = SessionEvent().apply { sessionId = "id1"}
        val expected = Record(FLOW_MAPPER_EVENT_TOPIC,sessionEvent.sessionId, FlowMapperEvent(sessionEvent))
        assertThat(RecordFactoryImpl().createFlowMapperSessionEventRecord(sessionEvent)).isEqualTo(expected)
    }
}