package net.corda.flow.external.events.handler

/**
 * [ExternalEventRecord] contains the [topic], [key] and [payload] to send to an external processor.
 *
 * @param topic The topic to send the event to.
 * @param key The key of the event. If left as `null`, the calling flow's flow id is used.
 * @param payload The payload to send.
 */
data class ExternalEventRecord(val topic: String, val key: Any?, val payload: Any) {

    constructor(topic: String, payload: Any) : this(topic, null, payload)
}