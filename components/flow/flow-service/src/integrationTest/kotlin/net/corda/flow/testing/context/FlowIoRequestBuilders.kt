package net.corda.flow.testing.context

import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.BOB_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CHARLIE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.FLOW_ID1
import net.corda.flow.testing.tests.REQUEST_ID1
import net.corda.virtualnode.toCorda

val ALICE_FLOW_KEY_MAPPER = "$REQUEST_ID1-${ALICE_HOLDING_IDENTITY.toCorda().shortHash}"
val BOB_FLOW_KEY_MAPPER = "$REQUEST_ID1-${BOB_HOLDING_IDENTITY.toCorda().shortHash}"
val CHARLIE_FLOW_KEY_MAPPER = "$REQUEST_ID1-${CHARLIE_HOLDING_IDENTITY.toCorda().shortHash}"

/**
 * Start a flow for [ALICE_HOLDING_IDENTITY]
 */
fun startFlow(setup: StepSetup): FlowIoRequestSetup {
    return setup.startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
}


