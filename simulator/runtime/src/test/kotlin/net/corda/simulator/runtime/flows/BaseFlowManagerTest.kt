package net.corda.simulator.runtime.flows

import net.corda.simulator.runtime.messaging.CloseableFlowMessaging
import net.corda.simulator.runtime.utils.accessField
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.Closeable
import java.security.AccessController
import java.security.PrivilegedExceptionAction

class BaseFlowManagerTest {

    companion object {

        private class MyCloseableFlowMessagingFlow : RPCStartableFlow {
            @CordaInject
            private val flowMessaging: FlowMessaging = mock<CloseableFlowMessaging>()

            override fun call(requestBody: RPCRequestData): String { return "result" }
        }

        private class MyStandaloneFlow : RPCStartableFlow {
            override fun call(requestBody: RPCRequestData): String { return "result" }
        }

        private class MyCloseableSubFlow : SubFlow<String> {
            @CordaInject
            private val flowMessaging: FlowMessaging = mock<CloseableFlowMessaging>()
            override fun call(): String { return "result" }


        }
    }

    @Test
    fun `should close any flow messaging service on flows`() {
        // Given a flow with flow messaging that's also closeable
        val flow = MyCloseableFlowMessagingFlow()

        // When we call it through the flow manager
        val flowManager = BaseFlowManager()
        val result = flowManager.call(mock(), flow)

        // Then we should get the result back
        assertThat(result, `is`("result"))

        // And all closeable services should have been closed
        val field = flow.accessField(FlowMessaging::class.java) ?: fail("No flow messaging service found")
        AccessController.doPrivileged(PrivilegedExceptionAction {
            field.isAccessible = true
        })
        val flowMessaging = field.get(flow) as Closeable
        verify(flowMessaging, times(1)).close()
    }

    @Test
    fun `should close any flow messaging service on subFlows`() {
        // Given a flow with flow messaging that's also closeable
        val subFlow = MyCloseableSubFlow()

        // When we call it through the flow manager
        val flowManager = BaseFlowManager()
        val result = flowManager.call(subFlow)

        // Then we should get the result back
        assertThat(result, `is`("result"))

        // And all closeable services should have been closed
        val field = subFlow.accessField(FlowMessaging::class.java) ?: fail("No flow messaging service found")
        AccessController.doPrivileged(PrivilegedExceptionAction {
            field.isAccessible = true
        })
        val flowMessaging = field.get(subFlow) as Closeable
        verify(flowMessaging, times(1)).close()
    }

    @Test
    fun `should not worry about any flow that does not have FlowMessaging`() {
        // Given a flow with no flow messaging
        val flow = MyStandaloneFlow()

        // When we call it through the flow manager
        val flowManager = BaseFlowManager()
        val result = flowManager.call(mock(), flow)

        // Then we should get the result back with no errors
        assertThat(result, `is`("result"))
    }
}