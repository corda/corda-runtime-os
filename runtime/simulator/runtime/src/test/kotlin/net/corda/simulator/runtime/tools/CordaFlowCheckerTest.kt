package net.corda.simulator.runtime.tools

import net.corda.simulator.exceptions.NoDefaultConstructorException
import net.corda.simulator.exceptions.NoSuspendableCallMethodException
import net.corda.simulator.exceptions.UnrecognizedFlowClassException
import net.corda.simulator.runtime.testflows.NonConstructableFlow
import net.corda.simulator.runtime.testflows.NonSuspendableFlow
import net.corda.simulator.runtime.testflows.ValidResponderFlow
import net.corda.simulator.runtime.testflows.ValidStartingFlow
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class CordaFlowCheckerTest {

    @Test
    fun `should error if the flow cannot be constructed`() {
        val flowClass = NonConstructableFlow::class.java
        assertThrows<NoDefaultConstructorException> { CordaFlowChecker().check(flowClass) }
    }

    @Test
    fun `should error if the flow is missing suspendable annotation on the call method`() {
        val flowClass = NonSuspendableFlow::class.java
        assertThrows<NoSuspendableCallMethodException> { CordaFlowChecker().check(flowClass) }
    }


    @Test
    fun `should pass valid starting flows`() {
        val flowClass = ValidStartingFlow::class.java
        assertDoesNotThrow { CordaFlowChecker().check(flowClass) }
    }

    @Test
    fun `should pass valid responder flows`() {
        val flowClass = ValidResponderFlow::class.java
        assertDoesNotThrow { CordaFlowChecker().check(flowClass) }
    }

    @Test
    fun `should pass valid subflows even without a default constructor`() {
        val subflowClass = ValidSubFlow::class.java
        assertDoesNotThrow { CordaFlowChecker().check(subflowClass) }
    }

    @Test
    fun `should error if flow is not a recognized flow`() {
        val flowClass = NotReallyAFlow::class.java
        assertThrows<UnrecognizedFlowClassException> { CordaFlowChecker().check(flowClass) }
    }

    class NotReallyAFlow : Flow
    class ValidSubFlow(private val r: String) : SubFlow<String> {
        @Suspendable
        override fun call(): String { return r }
    }
}