package net.corda.flow.testing.context

import net.corda.data.flow.FlowKey
import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.FLOW_ID1
import net.corda.flow.testing.tests.REQUEST_ID1

val ALICE_FLOW_KEY = FlowKey(REQUEST_ID1, ALICE_HOLDING_IDENTITY).toString()

/**
 * Start a flow for [ALICE_HOLDING_IDENTITY]
 */
fun startFlow(setup: StepSetup): FlowIoRequestSetup {
    return setup.startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
}


