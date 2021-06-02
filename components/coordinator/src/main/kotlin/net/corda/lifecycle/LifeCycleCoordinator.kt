package net.corda.lifecycle

interface LifeCycleCoordinator: LifeCycle {

    val batchSize: Int

    val lifeCycleProcessor: LifeCycleProcessor

    fun cancelTimer(key: String)

    fun postEvent(lifeCycleEvent: LifeCycleEvent)

    fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent)

}