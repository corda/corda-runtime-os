package net.corda.lifecycle

interface LifeCycleCoordinator: LifeCycle {

    val batchSize: Int

    val lifeCycleProcessor: (lifeCycleEvent: LifeCycleEvent) -> Unit

    val timeout: Long

    fun cancelTimer(key: String)

    fun postEvent(lifeCycleEvent: LifeCycleEvent)

    fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent)

}