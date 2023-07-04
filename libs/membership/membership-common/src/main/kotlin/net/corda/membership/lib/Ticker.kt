package net.corda.membership.lib

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

object Ticker {
    private data class Tick(
        val from: String,
        val to: String,
        val duration: Double,
    )
    private val logger = LoggerFactory.getLogger("QQQ")
    private val ticks = ConcurrentLinkedDeque<Tick>()
    private val lastTick = AtomicReference<Pair<String, Long>>()
    private var working: Boolean = false
    fun start(name: String) {
        working = true
        tick(name)
    }
    fun tick(name: String) {
        if(working) {
            return
        }
        val now = System.currentTimeMillis()
        lastTick.getAndUpdate {prevTick ->
            if(prevTick != null) {
                ticks.addLast(Tick(
                    prevTick.first,
                    name,
                    (now - prevTick.second).toDouble() / 1000.0
                ))
            }
            name to now
        }
    }
    fun end(name: String) {
        tick(name)
        working = false
        ticks.filter { it.duration > 0 }.sortedBy { it.duration }.takeLast(6).forEach {
            logger.info("\t $it")
        }
        lastTick.set(null)
        ticks.clear()
    }
}
