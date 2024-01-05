package net.corda.messaging.mediator.mocks

import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient

/**
 * A mock implementation of [MessagingClient] for testing purposes.
 * This client simulates network delays and provides predefined responses.
 *
 * @property id The identifier of this client.
 * @property delayTime The simulated delay time (in milliseconds) for the [send] method.
 * @property returnValue The predefined return value that the [send] method will return.
 */
class MockRPCClient(
    override val id: String,
    private val delayTime: Long,
    private val returnValue: MediatorMessage<*> =
        MediatorMessage("$id-test".toByteArray(Charsets.UTF_8))
) : MessagingClient {
    override fun send(message: MediatorMessage<*>): MediatorMessage<*> {
        Thread.sleep(delayTime)
        return returnValue
    }

    override fun close() {
        // Nothing to do here
    }
}
