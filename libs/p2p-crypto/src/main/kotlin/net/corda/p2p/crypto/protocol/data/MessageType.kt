package net.corda.p2p.crypto.protocol.data

enum class MessageType {
    /**
     * Step 1 of session authentication protocol.
     */
    INITIATOR_HELLO,
    /**
     * Step 2 of session authentication protocol.
     */
    RESPONDER_HELLO,
    /**
     * Step 3 of session authentication protocol.
     */
    INITIATOR_HANDSHAKE,
    /**
     * Step 4 of session authentication protocol.
     */
    RESPONDER_HANDSHAKE,
    /**
     * Any data message exchanged after the session authentication protocol has been completed.
     */
    DATA
}