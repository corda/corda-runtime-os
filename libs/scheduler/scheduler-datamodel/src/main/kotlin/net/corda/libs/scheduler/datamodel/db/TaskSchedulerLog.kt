package net.corda.libs.scheduler.datamodel.db

import java.time.Instant

interface TaskSchedulerLog {
    var name: String
    var schedulerId: String
    var lastScheduled: Instant
    var now: Instant
}