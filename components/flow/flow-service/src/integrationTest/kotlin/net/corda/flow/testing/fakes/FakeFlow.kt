package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.Flow

class FakeFlow: Flow<String> {
    override fun call(): String {
        return ""
    }
}