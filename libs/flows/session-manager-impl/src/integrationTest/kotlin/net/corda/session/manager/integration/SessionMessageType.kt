package net.corda.session.manager.integration

enum class SessionMessageType {
    INIT,
    CONFIRM,
    DATA,
    ERROR,
    CLOSE
}
