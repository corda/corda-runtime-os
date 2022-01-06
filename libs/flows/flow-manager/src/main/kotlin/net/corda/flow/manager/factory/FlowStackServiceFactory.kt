package net.corda.flow.manager.factory

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowStackService

interface FlowStackServiceFactory {

    fun create(checkpoint: Checkpoint): FlowStackService
}