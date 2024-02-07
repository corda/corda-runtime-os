package net.corda.messaging.mediator.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.messaging.api.mediator.MediatorTraceLog
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class MediatorBatchMetrics {

    companion object {
        private const val thresholdTimeMillis = 0L
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val mapper = ObjectMapper()
        private val lastReport = AtomicLong().apply { set(0) }
    }

    class BatchGroupMetric(
        val scheduledTime: Long,
        val batchSize: Int,
        val logs: MutableList<MediatorTraceLog.LoggedEvent>,
    ) {
        var startExecutionTime = 0L
        var completionTime = 0L

        fun start() {
            startExecutionTime = System.nanoTime()
        }

        fun complete() {
            completionTime = System.nanoTime()
        }
    }

    class Report {
        var pollBatchSize: Int = 0
        var pollTime: Long = 0
        var pollBatchExecutionTime: Long = 0
        var stateUpdatedTime: Long = 0
        var outputsCommittedTime: Long = 0
        var groups = mutableListOf<GroupReport>()
    }

    class GroupReport {
        var groupSize: Int = 0
        var groupStartOffset: Long = 0
        var groupExecutionTime: Long = 0
        var reportLogs = mutableListOf<LogReport>()
    }

    class LogReport {
        var id: String = ""
        var text: String = ""
        var timeOffset: Long = 0L
    }

    private val groupMetrics = mutableListOf<BatchGroupMetric>()
    private val startTime: Long = System.nanoTime()
    private var processStartTime = 0L
    private var batchSize = 0
    private var pollCompleteTime = 0L
    private var internalStatePersistedTime = 0L
    private var internalOutputsCommittedTime = 0L

    fun pollCompleted(batchSize: Int) {
        this.batchSize = batchSize
        processStartTime = System.nanoTime()
        pollCompleteTime = startTime.timeToNow()
    }

    fun createGroupBatch(batchSize: Int): BatchGroupMetric {
        return BatchGroupMetric(System.nanoTime(), batchSize, mutableListOf()).apply { groupMetrics.add(this) }
    }

    fun batchStatePersisted() {
        internalStatePersistedTime = processStartTime.timeToNow()
    }

    fun batchOutputsCommitted() {
        internalOutputsCommittedTime = processStartTime.timeToNow()
    }

    fun batchCompleted() {
        val exeTime = startTime.timeToNow().toMillis()
        val now = Instant.now().toEpochMilli()
        if (exeTime < thresholdTimeMillis || batchSize == 0 || (lastReport.get() + 1000) > now) {
            return
        }

        val report = Report().apply {
            this.pollBatchSize = batchSize
            this.pollTime = pollCompleteTime.toMillis()
            this.pollBatchExecutionTime = startTime.timeToNow().toMillis()
            this.stateUpdatedTime = internalStatePersistedTime.toMillis()
            this.outputsCommittedTime = internalOutputsCommittedTime.toMillis()
            this.groups = groupMetrics.map { grp ->
                GroupReport().apply {
                    this.groupSize = grp.batchSize
                    this.groupStartOffset = (grp.startExecutionTime - grp.scheduledTime).toMillis()
                    this.groupExecutionTime = (grp.completionTime - grp.startExecutionTime).toMillis()
                    this.reportLogs = grp.logs.map { loggedEvent ->
                        LogReport().apply {
                            id = loggedEvent.groupId
                            this.text = loggedEvent.text
                            this.timeOffset = loggedEvent.timeOffsetNanoTime.toMillis()
                        }
                    }.toMutableList()
                }
            }.toMutableList()
        }
        lastReport.set(Instant.now().toEpochMilli())
        log.info("Mediator threshold breached: '${mapper.writeValueAsString(report)}'")
    }

    private fun Long.toMillis(): Long {
        return Duration.ofNanos(this).toMillis()
    }

    private fun Long.timeToNow(): Long {
        return System.nanoTime() - this
    }
}
