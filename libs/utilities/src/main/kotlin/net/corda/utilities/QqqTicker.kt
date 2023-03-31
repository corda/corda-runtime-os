package net.corda.utilities

import net.corda.utilities.time.UTCClock
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.random.Random

object QqqTicker {
    private val id = Random.nextInt(1000)
    private val logger = LoggerFactory.getLogger("QQQ $id")

    private val clock = UTCClock()
    private val df = DecimalFormat("##.####%")

    private data class Tick(
        val stack: Throwable,
        val time: Instant,
        val name: String,
    ) {
        fun display(): String {
            return "$name (" +
                stack.stackTrace.take(5).takeLast(4).joinToString(separator = "->") {
                    "${it.fileName}:${it.lineNumber}"
                } + ")"
        }
    }

    private val ticks = ConcurrentLinkedDeque<Tick>()

    fun tick(name: String = "") {
        val tick = Tick(
            Exception("QQQ"),
            clock.instant(),
            name,
        )
        //val last = ticks.lastOrNull()
        ticks.push(tick)
        /*if (last != null) {
            logger.info(
                "From ${last.display()} to ${tick.display()} " +
                    "took ${Duration.between(last.time, tick.time).toMillis()} millis",
            )
        } else {
            logger.info("Started with ${tick.display()}")
        }*/
    }
    fun reset() {
        ticks.clear()
    }

    fun report() {
        val duration = Duration.between(ticks.last.time, ticks.first.time).toMillis()
        logger.info("Ticks Report - took $duration millis")
        val maxs = ticks.reversed().zipWithNext { last, tick ->
            val took = Duration.between(last.time, tick.time).toMillis()
            val per = took.toDouble() / duration.toDouble()
            if(per > 0.05) {
                val text = "\t From ${last.display()} to ${tick.display()} " +
                        "took $took millis (${df.format(per)})"
                logger.info(text)
                took to text
            } else {
                took to "short"
            }
        }.sortedBy {
            it.first
        }.takeLast(6)
            .map { it.second }
        logger.info("Maximal usage")
        maxs.forEach { text ->
            logger.info(text)
        }
    }
}
