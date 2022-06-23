package net.cordapp.testing.chat

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Generates a mock which will return the passed object to any call to parse json along the lines of
 * requestBody.getRequestBodyAs<T>(jsonMarshallingService) inside the Flow.
 * This method allows the injection of RPC parameters to a Flow without having to test/mock json masrshalling
 * whilst ensuring the Flow implementation under test is using the correct JsonMarshallingService itself.
 */
inline fun <reified T> FlowTestDependencyInjector.rpcRequestGenerator(parameterObject: T) = mock<RPCRequestData>()
    .also {
        whenever(
            it.getRequestBodyAs(this.serviceMock<JsonMarshallingService>(), T::class.java)
        ).thenReturn(parameterObject)
    }

/**
 * Sets up the FlowMessage mock associated with this injector such that it returns a flow session which is
 * also available to the test to verify expected messages are sent.
 * @return A mock FlowSession
 */
fun FlowTestDependencyInjector.expectFlowMessagesTo(member: MemberX500Name) = mock<FlowSession>().also {
    whenever(this.serviceMock<FlowMessaging>().initiateFlow(member)).thenReturn(it)
}

/**
 * Verifies a payload was sent via this FlowSession
 */
fun FlowSession.verifyMessageSent(payload: Any) {
    verify(this).send(payload)
}
