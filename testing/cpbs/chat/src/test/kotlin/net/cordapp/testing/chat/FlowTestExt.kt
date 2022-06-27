package net.cordapp.testing.chat

import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.fail
import org.mockito.kotlin.*

fun FlowEngine.withVirtualNodeName(name:String): FlowEngine {
    whenever(this.virtualNodeName).thenReturn(MemberX500Name.parse(name))
    return this
}

fun FlowSession.withCounterpartyName(name:String): FlowSession {
    whenever(this.counterparty).thenReturn(MemberX500Name.parse(name))
    return this
}

inline fun <reified T : Any> FlowSession.willReceive(payload: T): FlowSession {
    whenever(this.receive(T::class.java)).thenReturn(UntrustworthyData(payload))
    return this
}

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
 * Verifies a payload was sent via this FlowSession
 */
fun FlowSession.verifyMessageSent(payload: Any) {
    verify(this).send(payload)
}

/**
 * Sets up the FlowMessage mock associated with this injector such that it returns a flow session which is
 * also available to the test to verify expected messages are sent.
 * @return A mock FlowSession
 */
fun FlowTestDependencyInjector.expectFlowMessagesTo(member: MemberX500Name) = mock<FlowSession>().also {
    whenever(this.serviceMock<FlowMessaging>().initiateFlow(member)).thenReturn(it)
}

fun expectFlowMessagesFrom(member: MemberX500Name) = mock<FlowSession>().also {
        whenever(it.counterparty).thenReturn(member) }

fun validateProtocol(from: Flow, to: ResponderFlow)
{
    val annotationOfInitiating = from::class.java.getAnnotation(InitiatingFlow::class.java)
        ?: fail("InitiatingFlow ${from::class.java.name} has no @InitiatingFlow annotation")
    val annotationOfInitiatedBy = to::class.java.getAnnotation(InitiatedBy::class.java)
        ?: fail("InitiatedBy Flow ${to::class.java.name} has no @InitiatedBy annotation")

    if (annotationOfInitiating.protocol != annotationOfInitiatedBy.protocol) {
        fail ("Flow ${from::class.java.name} initiates protocol '${annotationOfInitiating.protocol}'" +
                " whilst ResponderFlow ${to::class.java.name} is initiated by protocol '${annotationOfInitiatedBy.protocol}'")
    }
}

/**
 * Helper to execute Flow call()s in parallel.
 * Executes every block concurrently. Will return once all blocks are complete.
 */
fun ExecuteConcurrently(vararg blocks: () -> Unit) {
    blocks.map { Thread { it() } }.onEach { it.start() }.onEach { it.join() }
}
