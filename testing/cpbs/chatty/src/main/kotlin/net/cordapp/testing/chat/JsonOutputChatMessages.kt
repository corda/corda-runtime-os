package net.cordapp.testing.chat

import net.cordapp.testing.chatcontract.IncomingChatMessage
import net.cordapp.testing.chatcontract.OutgoingChatMessage

data class Chat(val counterparty: String, var messages: List<Message>)

const val DIRECTION_INCOMING = "incoming"
const val DIRECTION_OUTGOING = "outgoing"

data class Message(val id: String, val direction: String, val content: String, val timestamp: String)

fun chats(incomingMessages: List<IncomingChatMessage>, outgoingMessages: List<OutgoingChatMessage>): List<Chat> {
    // Group incoming messages by sender
    // For each sender, create a chat, and map the messages into the chat message list
    var chats = incomingMessages.groupBy { it.sender }.entries.map {
        Chat(counterparty = it.key, messages = it.value.map {
            Message(
                id = it.id.toString(), direction = DIRECTION_INCOMING, content = it.message, timestamp = it.timestamp
            )
        })
    }

    // Group outgoing messages by recipient
    outgoingMessages.groupBy { it.recipient }.entries.forEach { outgoingMessageGroup ->
        // Map outgoing messages into the output format
        val messages = outgoingMessageGroup.value.map {
            Message(
                id = it.id.toString(), direction = DIRECTION_OUTGOING, content = it.message, timestamp = it.timestamp
            )
        }

        // Check if we have a chat already for this member, and if so add the list of outgoing messages and sort
        chats.find { it.counterparty == outgoingMessageGroup.key }?.let {
            it.messages = (it.messages + messages).sortedBy { it.timestamp }
        } ?: run {
            // Otherwise create a new chat and add the sorted message list
            chats += Chat(
                counterparty = outgoingMessageGroup.key,
                messages = messages.sortedBy { it.timestamp })
        }
    }

    return chats
}
