package net.corda.messaging.constants

/**
 * Types of subscription possible in the messaging library
 */
enum class SubscriptionType {
    //Compacted subscription
    COMPACTED,
    //Durable subscription
    DURABLE,
    //Event log subscription
    EVENT_LOG,
    // Pub-Sub subscription
    PUB_SUB,
    //Subscription responsible for sending RPC requests
    RPC_SENDER,
    //Subscription responsible for receiving RPC responses
    RPC_RESPONDER,
    //State and event subscription
    STATE_AND_EVENT;
}