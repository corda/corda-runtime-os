package net.corda.messaging.api.mediator

class MediatorTraceLog {
    companion object {
        private val eventLog = ThreadLocal<Context>()

        fun init(logs: MutableList<LoggedEvent>, groupScheduledTime: Long) {
            eventLog.set(Context(groupScheduledTime, System.nanoTime(), logs))
            recordEvent("N/A", "Group exe start", System.nanoTime() - groupScheduledTime)
        }

        private fun recordEvent(groupId: String, name: String, time: Long) {
            eventLog.get().also {
                it.log.add(LoggedEvent(groupId, name, time))
            }
        }

        fun recordEvent(groupId: String, name: String) {
            eventLog.get().also {
                it.log.add(LoggedEvent(groupId, name, System.nanoTime() - it.startNanoTime))
            }
        }
    }

    data class Context(val scheduleTimeNanoTime: Long, val startNanoTime: Long, val log: MutableList<LoggedEvent>)
    data class LoggedEvent(val groupId: String, val text: String, val timeOffsetNanoTime: Long)
}
