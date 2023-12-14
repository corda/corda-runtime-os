package net.corda.metrics

import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.binder.BaseUnits
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.logging.LoggingRegistryConfig
import io.micrometer.core.instrument.util.DoubleFormat.decimalOrNan
import io.micrometer.core.instrument.util.TimeUtils
import java.lang.StringBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.StreamSupport
import kotlin.math.ln

class MyLoggingMeterRegistry(private val loggingSink: Consumer<String>, private val scraper: () -> String) : LoggingMeterRegistry(loggingSink) {

    override fun publish() {
        loggingSink.accept("PUBLISHING\n\n\n\n\n")
//        val printer = CsvPrinter2(loggingSink)
//        meters.stream().sorted { m1, m2 ->
//            val res = m1.id.type.compareTo(m2.id.type)
//            if (res == 0)
//                m1.id.name.compareTo(m2.id.name)
//            res
//        }
//
//        meters.forEach { meter -> printer.print(meter) }
        loggingSink.accept(scraper.invoke())
    }
}

abstract class MeterPrinter(val sink: Consumer<String>) {

    private val config = LoggingRegistryConfig.DEFAULT
    val baseTimeUnit = TimeUnit.MILLISECONDS
    abstract fun print(meter: Meter)

    fun time(time: Double): String? {
        return TimeUtils
            .format(Duration.ofNanos(TimeUtils.convert(time, TimeUnit.MILLISECONDS, TimeUnit.NANOSECONDS).toLong()))
    }

    fun rate(meter: Meter, rate: Double): String? {
        return humanReadableBaseUnit(meter,rate / config.step().seconds.toDouble()) + "/s"
    }

    fun unitlessRate(rate: Double): String? {
        return decimalOrNan(rate / config.step().seconds.toDouble()) + "/s"
    }

    fun value(meter: Meter, value: Double): String? {
        return humanReadableBaseUnit(meter, value)
    }
    private fun humanReadableByteCount(bytes: Double): String {
        val unit = 1024
        if (bytes < unit || java.lang.Double.isNaN(bytes)) return decimalOrNan(bytes) + " B"
        val exp = (Math.log(bytes) / ln(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1].toString() + "i"
        return decimalOrNan(bytes / Math.pow(unit.toDouble(), exp.toDouble())) + " " + pre + "B"
    }

    private fun humanReadableBaseUnit(meter: Meter, value: Double): String {
        val baseUnit = meter.id.baseUnit?.toString()
        return if (BaseUnits.BYTES == baseUnit) {
            humanReadableByteCount(value)
        } else decimalOrNan(value) + " $baseUnit"
    }
}

class CsvPrinter(sink: Consumer<String>) : MeterPrinter(sink) {

    override fun print(meter: Meter) {
        meter.use(
            { gauge: Gauge ->
                sink.accept("${meter.id}, ${gauge.value()}")
            },
            { counter: Counter ->
                sink.accept("${meter.id}, ${rate(meter, counter.count())}")
            },
            { timer: Timer ->
                val snapshot = timer.takeSnapshot()
                val count = snapshot.count()
                sink.accept("${meter.id}, ${unitlessRate(count.toDouble())}, ${snapshot.mean(baseTimeUnit)}, ${snapshot.max(baseTimeUnit)}")
            },
            { summary: DistributionSummary ->
                val snapshot = summary.takeSnapshot()
                val count = snapshot.count()
                sink.accept("${meter.id}, ${unitlessRate(count.toDouble())}, ${snapshot.mean(baseTimeUnit)}, ${snapshot.max(baseTimeUnit)}")
            },
            { longTaskTimer: LongTaskTimer ->
                val activeTasks = longTaskTimer.activeTasks()
                sink.accept("${meter.id}, $activeTasks, ${longTaskTimer.duration(baseTimeUnit)}")
            },
            { timeGauge: TimeGauge ->
                val value = timeGauge.value(baseTimeUnit)
                sink.accept("${meter.id}, ${time(value)}")
            },
            { counter: FunctionCounter ->
                val count = counter.count()
                sink.accept("${meter.id}, ${rate(meter, count)}")
            },
            { timer: FunctionTimer ->
                val count = timer.count()
                sink.accept("${meter.id}, ${rate(meter, count)}, ${timer.mean(baseTimeUnit)}")
            },
            { m: Meter? ->
                m?.let { sink.accept(writeMeter(m)) }
            })
    }

    private fun writeMeter(meter: Meter): String {
        return StreamSupport.stream(meter.measure().spliterator(), false).map { ms: Measurement ->
            val msLine = ms.statistic.tagValueRepresentation
            when (ms.statistic) {
                Statistic.TOTAL, Statistic.MAX -> {}
                Statistic.VALUE -> {
                    sink.accept("${meter.id}, $msLine, ${value(meter, ms.value)}")
                }
                Statistic.TOTAL_TIME -> {}
                Statistic.DURATION -> {
                    sink.accept("${meter.id}, $msLine, ${time(ms.value)}")
                }
                Statistic.COUNT -> {
                    sink.accept("${meter.id}, ${rate(meter, ms.value)}")
                }
                else -> {
                    sink.accept("${meter.id}, $msLine, ${decimalOrNan(ms.value)}")
                }
            }
        }.toString()
    }
}

class CsvPrinter2(sink: Consumer<String>) : MeterPrinter(sink) {
    override fun print(meter: Meter) {
        val measurements = StringBuilder().apply {
            this.append("\"")
            meter.measure().forEach { measurement ->
                this.append("${measurement.statistic}=${measurement.value},")
            }
            this.deleteCharAt(this.length - 1)
            this.append("\"")
//            this.replace(this.length - 1, this.length - 1, "\"")
            this.toString()
        }

        val tags = StringBuilder().apply {
            this.append("\"")
            meter.id.tags.forEach {
                this.append("${it.key}=${it.value},")
            }
//            this.replace(this.length - 1, this.length - 1, "\"")
            this.deleteCharAt(this.length - 1)
            this.append("\"")
            this.toString()
        }

        sink.accept("${meter.id.name}, ${meter.id.type}, $measurements, $tags")
    }
}

