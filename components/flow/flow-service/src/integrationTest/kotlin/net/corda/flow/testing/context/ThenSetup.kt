package net.corda.flow.testing.context

interface ThenSetup {
    fun expectOutputForFlow(flowId: String, outputAssertions: OutputAssertions.() -> Unit)
}