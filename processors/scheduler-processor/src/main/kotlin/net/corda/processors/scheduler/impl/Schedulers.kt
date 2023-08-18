package net.corda.processors.scheduler.impl

import net.corda.components.scheduler.Schedule
import net.corda.components.scheduler.Scheduler
import net.corda.components.scheduler.SchedulerFactory
import net.corda.configuration.read.ConfigChangedEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class Schedulers(
    private val schedules: List<Schedule>,
    private val schedulerFactory: SchedulerFactory,
) {
    private val configuredSchedulers = ConcurrentHashMap<String, Scheduler>()

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun createAndStartSchedules() {
        val duplicateTasks = schedules.groupBy { it.taskName }.filter { it.value.count() > 1 }.map { it.key }
        if(duplicateTasks.isNotEmpty()) throw IllegalArgumentException("Tasks must be unique, but duplicate(s) detected: $duplicateTasks")

        schedules.forEach { schedule ->
            // TODO - this will need to handle updating schedules
            configuredSchedulers.computeIfAbsent(schedule.taskName) {
                logger.info("Creating scheduler for ${schedule.taskName}")
                schedulerFactory.create(schedule)
            }.start().also {
                logger.info("Starting scheduler for ${schedule.taskName}")
            }
        }
    }

    fun onConfigChanged(event: ConfigChangedEvent) {
        // TODO - CORE-16331: plug in config
        logger.warn("ConfigChangeEvent not yet implemented: $event")
        // createAndStartSchedules()
    }

    fun start() {
        createAndStartSchedules()
    }

    fun stop() {
        configuredSchedulers.forEach {
            logger.info("Stopping scheduler for ${it.key}")
            it.value.stop()
        }
    }
}