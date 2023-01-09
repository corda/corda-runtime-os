package net.cordacon.example

import net.corda.v5.application.flows.Flow
import net.cordacon.example.rollcall.AbsenceCallResponderFlow

class AbsenceCallResponderFlowTest : ResponderFlowDelegateTest() {
    override val protocol: String = "absence-call"
    override val flowClass: Class<out Flow> = AbsenceCallResponderFlow::class.java
}