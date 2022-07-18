package net.cordapp.testing.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class JsonOutputChatMessages {

    @Test
    fun `Json output message structure`() {
        val chats = chats(outgoingMessages = outgoingMessages(), incomingMessages = incomingMessages())

        assertThat(chats.size).isEqualTo(3)
        with(chats[0]) {
            assertThat(counterparty).isEqualTo("Bob")
            verifyBobsMessages()
        }
        with(chats[1]) {
            assertThat(counterparty).isEqualTo("Alice")
            verifyAlicesMessages()
        }
        with(chats[2]) {
            assertThat(counterparty).isEqualTo("Charlie")
            verifyCharliesMessages()
        }
    }

    private fun Chat.verifyCharliesMessages() {
        assertThat(messages.size).isEqualTo(2)
        with(messages[0]) {
            assertThat(direction).isEqualTo("outgoing")
            assertThat(content).isEqualTo("message to charlie 2")
            assertThat(timestamp).isEqualTo("10")
        }
        with(messages[1]) {
            assertThat(direction).isEqualTo("outgoing")
            assertThat(content).isEqualTo("message to charlie 1")
            assertThat(timestamp).isEqualTo("15")
        }
    }

    private fun Chat.verifyAlicesMessages() {
        assertThat(messages.size).isEqualTo(4)
        with(messages[0]) {
            assertThat(direction).isEqualTo("incoming")
            assertThat(content).isEqualTo("message from alice 2")
            assertThat(timestamp).isEqualTo("10")
        }
        with(messages[1]) {
            assertThat(direction).isEqualTo("outgoing")
            assertThat(content).isEqualTo("message to alice 2")
            assertThat(timestamp).isEqualTo("15")
        }
        with(messages[2]) {
            assertThat(direction).isEqualTo("incoming")
            assertThat(content).isEqualTo("message from alice 1")
            assertThat(timestamp).isEqualTo("20")
        }
        with(messages[3]) {
            assertThat(direction).isEqualTo("outgoing")
            assertThat(content).isEqualTo("message to alice 1")
            assertThat(timestamp).isEqualTo("25")
        }
    }

    private fun Chat.verifyBobsMessages() {
        assertThat(messages.size).isEqualTo(6)
        with(messages[0]) {
            assertThat(direction).isEqualTo("incoming")
            assertThat(content).isEqualTo("message from bob 2")
            assertThat(timestamp).isEqualTo("10")
        }
        with(messages[1]) {
            assertThat(direction).isEqualTo("outgoing")
            assertThat(content).isEqualTo("message to bob 2")
            assertThat(timestamp).isEqualTo("15")
        }
        with(messages[2]) {
            assertThat(direction).isEqualTo("incoming")
            assertThat(content).isEqualTo("message from bob 3")
            assertThat(timestamp).isEqualTo("20")
        }
        with(messages[3]) {
            assertThat(direction).isEqualTo("outgoing")
            assertThat(content).isEqualTo("message to bob 3")
            assertThat(timestamp).isEqualTo("25")
        }
        with(messages[4]) {
            assertThat(direction).isEqualTo("incoming")
            assertThat(content).isEqualTo("message from bob 1")
            assertThat(timestamp).isEqualTo("30")
        }
        with(messages[5]) {
            assertThat(direction).isEqualTo("outgoing")
            assertThat(content).isEqualTo("message to bob 1")
            assertThat(timestamp).isEqualTo("35")
        }
    }

    fun outgoingMessages(): List<OutgoingChatMessage> {
        val messsage1 = OutgoingChatMessage(
            id = UUID.randomUUID(),
            recipient = "Bob", message = "message to bob 1", timestamp = "35"
        )
        val messsage2 = OutgoingChatMessage(
            id = UUID.randomUUID(),
            recipient = "Bob", message = "message to bob 2", timestamp = "15"
        )
        val messsage3 = OutgoingChatMessage(
            id = UUID.randomUUID(),
            recipient = "Bob", message = "message to bob 3", timestamp = "25"
        )
        val messsage4 = OutgoingChatMessage(
            id = UUID.randomUUID(),
            recipient = "Alice", message = "message to alice 1", timestamp = "25"
        )
        val messsage5 = OutgoingChatMessage(
            id = UUID.randomUUID(),
            recipient = "Alice", message = "message to alice 2", timestamp = "15"
        )
        val messsage6 = OutgoingChatMessage(
            id = UUID.randomUUID(),
            recipient = "Charlie", message = "message to charlie 1", timestamp = "15"
        )
        val messsage7 = OutgoingChatMessage(
            id = UUID.randomUUID(),
            recipient = "Charlie", message = "message to charlie 2", timestamp = "10"
        )

        return listOf(messsage1, messsage2, messsage3, messsage4, messsage5, messsage6, messsage7)
    }

    fun incomingMessages(): List<IncomingChatMessage> {
        val messsage1 = IncomingChatMessage(
            id = UUID.randomUUID(),
            sender = "Bob", message = "message from bob 1", timestamp = "30"
        )
        val messsage2 = IncomingChatMessage(
            id = UUID.randomUUID(),
            sender = "Bob", message = "message from bob 2", timestamp = "10"
        )
        val messsage3 = IncomingChatMessage(
            id = UUID.randomUUID(),
            sender = "Bob", message = "message from bob 3", timestamp = "20"
        )
        val messsage4 = IncomingChatMessage(
            id = UUID.randomUUID(),
            sender = "Alice", message = "message from alice 1", timestamp = "20"
        )
        val messsage5 = IncomingChatMessage(
            id = UUID.randomUUID(),
            sender = "Alice", message = "message from alice 2", timestamp = "10"
        )

        return listOf(messsage1, messsage2, messsage3, messsage4, messsage5)
    }
}