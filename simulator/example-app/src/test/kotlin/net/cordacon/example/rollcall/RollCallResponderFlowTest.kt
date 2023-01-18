package net.cordacon.example.rollcall

import net.corda.v5.application.flows.Flow

class RollCallResponderFlowTest : ResponderFlowDelegateTest() {

    override val protocol: String = "roll-call"
    override val flowClass: Class<out Flow> = RollCallResponderFlow::class.java

}