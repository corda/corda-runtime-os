package org.example

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

@Component(name = "test.cpb.2")
class FlowTwoBeta : Flow<String> {
    override fun call(): String {
        return "flow two beta"
    }
}
