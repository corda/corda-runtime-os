package net.cordapp.testing.chatframework

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Generates a mock which will return the passed object to any call to parse json along the lines of
 * requestBody.getRequestBodyAs<T>(jsonMarshallingService) inside the Flow.
 * This method allows the injection of RPC parameters to a Flow without having to test/mock json masrshalling
 * whilst ensuring the Flow implementation under test is using the correct JsonMarshallingService itself.
 * Typical use would be to pass the output of this method directly to the call() invocation on a Flow:
 * <pre>
 *     flow.call(
 *         flowMockHelper.rpcRequestGenerator(
 *             OutgoingChatMessage(recipientX500Name = RECIPIENT_X500_NAME)
 *         )
 *     }
 * </pre>
 * @return A mock RPCRequestData set up to return the correct object when queried for it
 */
inline fun <reified T> FlowMockHelper.rpcRequestGenerator(parameterObject: T) = mock<RPCRequestData>()
    .also {
        whenever(
            it.getRequestBodyAs(this.serviceMock<JsonMarshallingService>(), T::class.java)
        ).thenReturn(parameterObject)
    }

/**
 * Sets up the FlowMessage mock associated with this FlowMockHelper such that it returns a FlowSession which is
 * also available to the test to verify expected messages are sent.
 * @return The mock FlowSession which will be returned by the mock FlowMessage. This is useful to retain if you wish to
 * verify() any actions were performed on it after the Flow exits.
 */
fun FlowMockHelper.expectFlowMessagesTo(memberX500Name: MemberX500Name) = mock<FlowSession>().also {
    whenever(this.serviceMock<FlowMessaging>().initiateFlow(memberX500Name)).thenReturn(it)
}
