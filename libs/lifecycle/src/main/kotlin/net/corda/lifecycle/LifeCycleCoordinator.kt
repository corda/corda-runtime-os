package net.corda.lifecycle

interface LifeCycleCoordinator: LifeCycle {

    val batchSize: Int

    val lifeCycleProcessor: (LifeCycleEvent, LifeCycleCoordinator) -> Unit

    val timeout: Long

    fun cancelTimer(key: String)

    fun postEvent(LifeCycleEvent: LifeCycleEvent)

    fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent)

}