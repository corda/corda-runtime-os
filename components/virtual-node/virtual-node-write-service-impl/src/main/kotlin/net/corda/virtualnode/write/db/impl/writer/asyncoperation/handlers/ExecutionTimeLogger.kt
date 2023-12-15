package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.tracing.trace
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.slf4j.Logger

internal class ExecutionTimeLogger(
    private val operation: String,
    private val vNodeName: String,
    private val creationTime: Long,
    private val logger: Logger,
    private val clock: Clock = UTCClock()
) {
    fun <T> measureExecTime(stage: String, call: () -> T): T {
        return trace(stage) {
            val start = clock.instant().toEpochMilli()
            val result = call()
            val end = clock.instant().toEpochMilli()
            logger.debug("[$operation $vNodeName] $stage took ${end - start}ms, elapsed ${end - creationTime}ms")
            result
        }
    }
}
