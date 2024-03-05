package net.corda.messaging.constants

object MetricsConstants {
    // Pattern types, to use with MessagePatternType tag
    const val PUB_SUB_PATTERN_TYPE = "PubSub"
    const val COMPACTED_PATTERN_TYPE = "Compacted"
    const val DURABLE_PATTERN_TYPE = "Durable"
    const val RPC_PATTERN_TYPE = "RPC"
    const val STATE_AND_EVENT_PATTERN_TYPE = "StateAndEvent"
    const val EVENT_MEDIATOR_TYPE = "EventMediator"

    // Operation types, to use with OperationName tag
    const val ON_NEXT_OPERATION = "onNext"
    const val ON_SNAPSHOT_OPERATION = "onSnapshot"
    const val BATCH_PROCESS_OPERATION = "batchProcess"
    const val RPC_SENDER_OPERATION = "rpcSender"
    const val RPC_RESPONDER_OPERATION = "rpcResponder"
    const val EVENT_POLL_OPERATION = "eventPoll"
    const val STATE_POLL_OPERATION = "statePoll"
    const val ASYNC_EVENT_WAIT_TIME = "asyncEventWaitTime"
    const val PROCESS_ASYNC_EVENTS_TIME = "processAsyncEventsTime"
    const val PROCESS_SINGLE_ASYNC_EVENT_WAIT_TIME = "processSingleAsyncEventWaitTime"
    const val PROCESS_SINGLE_ASYNC_EVENT_TIME = "processSingleAsyncEventTime"
    const val PROCESS_SYNC_EVENTS_TIME = "processSyncEventsTime"
    const val PROCESS_SINGLE_SYNC_EVENT_TIME = "processSingleSyncEventTime"
    const val PERSIST_STATE_WAIT_TIME = "persistStateWaitTime"
    const val PERSIST_STATE_TIME = "persistStateTime"
    const val SEND_ASYNC_EVENTS_WAIT_TIME = "sendAsyncEventsWaitTime"
    const val SEND_ASYNC_EVENTS_TIME = "sendAsyncEventsTime"
    const val SEND_SINGLE_ASYNC_EVENT_TIME = "sendSingleAsyncEventTime"
}
