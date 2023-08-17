package net.corda.processors.scheduler.impl

import net.corda.components.scheduler.Schedule
import net.corda.components.scheduler.Scheduler
import net.corda.components.scheduler.SchedulerFactory
import net.corda.configuration.read.ConfigChangedEvent
import org.slf4j.LoggerFactory

class Schedulers(
    schedules: List<Schedule>,
    schedulerFactory: SchedulerFactory,
) {
    private val configuredSchedulers: List<Scheduler>
    init {
        val duplicateTasks = schedules.groupBy { it.taskName }.filter { it.value.count() > 1 }.map { it.key }
        if(duplicateTasks.isNotEmpty()) throw IllegalArgumentException("Tasks must be unique, but duplicate(s) detected: $duplicateTasks")

        logger.info("Creating schedulers for: $schedules")
        configuredSchedulers = schedules.map {
            schedulerFactory.create(it)
        }
    }
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    fun onConfigChanged(event: ConfigChangedEvent) {
        // TODO - CORE-16331: plug in config
        logger.warn("ConfigChangeEvent not yet implemented: $event")
    }

    fun start() {
        configuredSchedulers.forEach {
            logger.info("Starting scheduler for ${it.schedule.taskName}")
            it.start()
        }
    }

    fun stop() {
        configuredSchedulers.forEach {
            logger.info("Stopping scheduler for ${it.schedule.taskName}")
            it.stop()
        }
    }
}