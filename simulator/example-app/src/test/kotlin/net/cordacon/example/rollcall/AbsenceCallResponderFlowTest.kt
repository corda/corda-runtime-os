package net.cordacon.example.rollcall

import net.corda.v5.application.flows.Flow

class AbsenceCallResponderFlowTest : ResponderFlowDelegateTest() {
    override val protocol: String = "absence-call"
    override val flowClass: Class<out Flow> = AbsenceCallResponderFlow::class.java
}