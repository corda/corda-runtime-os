package net.corda.flow.state

import net.corda.v5.application.flows.FlowContextProperties

/**
 * Accessor for internal Corda methods to get to internal [FlowContext] from the public facing [FlowContextProperties]
 * in order to set platform properties.
 */
internal val FlowContextProperties.flowContext: FlowContext
    get() {
        require(this is FlowContext) { "FlowContextProperties implementation type is not recognised" }
        return this
    }
