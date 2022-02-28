package net.corda.messaging.constants

enum class SubscriptionType {
    COMPACTED,
    DURABLE,
    EVENT_LOG,
    PUB_SUB,
    RPC_SENDER,
    RPC_RESPONDER,
    STATE_AND_EVENT;
}