//package net.corda.metrics
//
//import io.micrometer.core.instrument.Clock
//import io.micrometer.core.instrument.Meter
//import io.micrometer.core.instrument.binder.BaseUnits
//import io.micrometer.core.instrument.logging.LoggingRegistryConfig
//import io.micrometer.core.instrument.step.StepMeterRegistry
//import io.micrometer.core.instrument.step.StepRegistryConfig
//import io.micrometer.core.instrument.util.DoubleFormat.decimalOrNan
//import io.micrometer.core.instrument.util.NamedThreadFactory
//import io.micrometer.core.instrument.util.TimeUtils
//import org.slf4j.LoggerFactory
//import java.time.Duration
//import java.util.concurrent.ThreadFactory
//import java.util.concurrent.TimeUnit
//import java.util.function.Consumer
//import kotlin.math.ln
//
//
//class FileMeterRegistry private constructor(val config: StepRegistryConfig,
//                                            val clock: Clock,
//                                            val threadFactory: ThreadFactory,
//                                            val loggingSink: Consumer<String>) : StepMeterRegistry(config, clock) {
//
//    companion object {
//        private val log = LoggerFactory.getLogger(this::class.java.name)
//    }
//    constructor() : this(LoggingRegistryConfig.DEFAULT, Clock.SYSTEM)
//
//    constructor(config: LoggingRegistryConfig, clock: Clock) : this(config, clock, log::info)
//
//    constructor(loggingSink: Consumer<String>) : this(LoggingRegistryConfig.DEFAULT, Clock.SYSTEM, loggingSink)
//
//    constructor(config: LoggingRegistryConfig, clock: Clock, loggingSink: Consumer<String>) : this(config, clock, NamedThreadFactory("file-metrics-publisher"), loggingSink)
//
//    override fun getBaseTimeUnit(): TimeUnit {
//        return TimeUnit.MILLISECONDS
//    }
//
//    override fun publish() {
//        println("PUBLISHING METRICS!!!")
//        println("REGISTERED METERS SIZE=${meters.size}")
//        meters.forEach { m ->
//            println("Printing")
//            loggingSink.accept(m.id.toString())
//            m.measure().forEach {
//                loggingSink.accept(it.statistic.toString())
//            }
//        }
//    }
//
//    inner class Printer(val meter: Meter) {
//
//        fun time(time: Double): String {
//            return TimeUtils.format(Duration.ofNanos(TimeUtils.convert(time, baseTimeUnit, TimeUnit.NANOSECONDS).toLong()))
//        }
//
//        fun rate(rate: Double): String {
//            return humanReadableBaseUnit(rate / config.step().seconds.toDouble()) + "/s"
//        }
//
//        fun unitlessRate(rate: Double): String {
//            return decimalOrNan(rate / config.step().seconds.toDouble()) + "/s"
//        }
//
//        fun value(value: Double): String {
//            return humanReadableBaseUnit(value)
//        }
//
//        private fun humanReadableByteCount(bytes: Double): String {
//            val unit = 1024
//            if (bytes < unit || java.lang.Double.isNaN(bytes)) return decimalOrNan(bytes) + " B"
//            val exp = (ln(bytes) / ln(unit.toDouble())).toInt()
//            val pre = "KMGTPE"[exp - 1].toString() + "i"
//            return decimalOrNan(bytes / Math.pow(unit.toDouble(), exp.toDouble())) + " " + pre + "B"
//        }
//
//        private fun humanReadableBaseUnit(value: Double): String {
//            val baseUnit = meter.id.baseUnit
//            return if (BaseUnits.BYTES == baseUnit) {
//                humanReadableByteCount(value)
//            } else decimalOrNan(value) + if (baseUnit != null) " $baseUnit" else ""
//        }
//    }
//}
//
