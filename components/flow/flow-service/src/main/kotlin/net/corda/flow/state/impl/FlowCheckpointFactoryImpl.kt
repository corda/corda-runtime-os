package net.corda.flow.state.impl

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowCheckpointFactory
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Suppress("Unused")
@Component(service = [FlowCheckpointFactory::class])
class FlowCheckpointFactoryImpl : FlowCheckpointFactory {
    override fun create(checkpoint: Checkpoint?): FlowCheckpoint {
        return FlowCheckpointImpl(checkpoint) { Instant.now() }
    }
}