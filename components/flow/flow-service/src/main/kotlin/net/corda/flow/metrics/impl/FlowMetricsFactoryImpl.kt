package net.corda.flow.metrics.impl

import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.metrics.FlowMetricsFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [FlowMetricsFactory::class])
class FlowMetricsFactoryImpl : FlowMetricsFactory {

    override fun create(eventRecordTimestamp: Long, flowCheckpoint: FlowCheckpoint): FlowMetrics {
        return FlowMetricsImpl(
            UTCClock(),
            FlowMetricsRecorderImpl(flowCheckpoint),
            flowCheckpoint,
            eventRecordTimestamp
        )
    }
}