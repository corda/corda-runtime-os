package net.corda.messagebus.api

interface TopicPartition {
    fun partition(): Int
    fun topic(): String
}
