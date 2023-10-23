package net.corda.session.manager.integration

enum class SessionMessageType {
    COUNTERPARTY_INFO,
    CONFIRM,
    DATA,
    ERROR,
    CLOSE
}
