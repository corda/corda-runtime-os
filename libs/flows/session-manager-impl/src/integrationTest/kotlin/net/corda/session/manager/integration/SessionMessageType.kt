package net.corda.session.manager.integration

enum class SessionMessageType {
    INIT,
    DATA,
    ACK,
    ERROR,
    CLOSE
}
