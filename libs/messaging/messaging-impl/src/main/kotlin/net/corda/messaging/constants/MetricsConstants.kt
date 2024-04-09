package net.corda.messaging.constants

object MetricsConstants {
    // Pattern types, to use with MessagePatternType tag
    const val PUB_SUB_PATTERN_TYPE = "PubSub"
    const val COMPACTED_PATTERN_TYPE = "Compacted"
    const val DURABLE_PATTERN_TYPE = "Durable"
    const val EVENT_SOURCE_PATTERN_TYPE = "Event"
    const val RPC_PATTERN_TYPE = "RPC"
    const val STATE_AND_EVENT_PATTERN_TYPE = "StateAndEvent"
    const val EVENT_MEDIATOR_TYPE = "EventMediator"

    // Operation types, to use with OperationName tag
    const val ON_NEXT_OPERATION = "onNext"
    const val ON_SNAPSHOT_OPERATION = "onSnapshot"
    const val BATCH_PROCESS_OPERATION = "batchProcess"
    const val RPC_SENDER_OPERATION = "rpcSender"
    const val RPC_RESPONDER_OPERATION = "rpcResponder"
}
