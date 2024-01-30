package net.corda.p2p.linkmanager.sessions

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object Ticker {
    private data class Tick(
        val started: Long = System.currentTimeMillis(),
        val caused: Exception? = Exception("QQQ"),
    ) {
        val name by lazy {
            if (caused == null) {
                "|"
            } else {
                val s = caused.stackTrace.get(2)
                "${s.className}::${s.methodName} ${s.fileName}:${s.lineNumber}"
            }
        }
    }
    private val current = ThreadLocal<Tick>()
    private val durations = ConcurrentHashMap<String, Dur>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private data class Dur(
        val total: Long,
        val totalTime: Double,
    ) {
        val avg by lazy {
            totalTime / total
        }
    }
    fun tick() {
        add(Tick())
    }
    fun done() {
        add(Tick(
            caused = null
        ))
    }

    init {
        scheduler.scheduleAtFixedRate({ report() }, 1, 5, TimeUnit.MINUTES)
    }

    fun report() {
        durations.entries.sortedBy {
            -it.value.avg
        }.take(20)
            .forEach {
                println("QQQ ${it.key} took ${it.value.avg} (${it.value.total})")
            }
        durations.clear()
    }
    private fun add(tick: Tick) {
        val latest = current.get()
        if (tick.caused != null) {
            current.set(tick)
        } else {
            current.set(null)
        }
        if (latest != null) {
            val name = "${latest.name}->${tick.name}"
            durations.compute(name) { k, v ->
                if (v == null) {
                    Dur(
                        1,
                        tick.started.toDouble() - latest.started.toDouble()
                    )
                } else {
                    Dur(
                        totalTime = v.totalTime + tick.started - latest.started,
                        total = v.total + 1,
                    )
                }
            }
        }
    }
}