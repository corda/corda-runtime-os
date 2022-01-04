package com.example

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

@Component(name = "test.cpb.3")
class FlowThreeBeta : Flow<String> {
    override fun call(): String {
        return "flow three beta"
    }
}
