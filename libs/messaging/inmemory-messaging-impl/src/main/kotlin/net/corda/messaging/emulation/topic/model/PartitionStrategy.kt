package net.corda.messaging.emulation.topic.model

abstract class PartitionStrategy {
    companion object {
        val modulo = object : PartitionStrategy() {
            override fun getPartitionMapper(consumerCount: Int): (Int) -> Int = { it % consumerCount }
        }

        val allInFirst = object : PartitionStrategy() {
            override fun getPartitionMapper(consumerCount: Int): (Int) -> Int = { 0 }
        }
    }
    abstract fun getPartitionMapper(consumerCount: Int): (Int) -> Int
}
