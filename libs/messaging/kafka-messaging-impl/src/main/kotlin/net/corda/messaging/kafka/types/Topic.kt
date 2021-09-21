package net.corda.messaging.kafka.types

class Topic(val prefix: String, val suffix: String) {
    val topic
        get() = prefix + suffix
}