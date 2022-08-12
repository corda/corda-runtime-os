package net.corda.testutils.tools

import net.corda.testutils.exceptions.UnexpectedRequestException
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.unwrap

/**
 * This fake responder can be uploaded to FakeCorda to respond to a given protocol.
 * It can be set up to respond to particular messages by sending back a list of responses
 * (the list can be empty).
 *
 * @T the request type
 * @U the response type
 */
class ResponderMock<T, U : Any> : ResponderFlow {

    private val responses : MutableMap<T, List<U>> = mutableMapOf()

    /**
     * Sets up a list of responses that will be sent in sequence for a matching request.
     *
     * @request the request for which to return a response
     * @responses a list of responses
     */
    fun whenever(request: T, responses: List<U>) {
        this.responses[request] = responses
    }

    override fun call(session: FlowSession) {
        val message : UntrustworthyData<*> = session.receive<Any>()
        val unwrapped = message.unwrap { it }
        val replies = responses[unwrapped]
            ?: throw UnexpectedRequestException(unwrapped.toString())

        replies.forEach { session.send(it) }
    }
}