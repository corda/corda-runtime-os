package net.corda.lifecycle

/**
 * This interface defines a component as coordinator of [LifeCycleEvent] events
 * processed by the [lifeCycleProcessor].
 *
 * Events are executed in sequence they are submitted calling [postEvent],
 * or when planned calling [setTimer].
 *
 */
interface LifeCycleCoordinator : LifeCycle {

    /**
     * Define the method processing the events of this coordinator.
     */
    val lifeCycleProcessor: (LifeCycleEvent, LifeCycleCoordinator) -> Unit

    fun cancelTimer(key: String)

    /**
     * Submit an event to be processed by [lifeCycleProcessor] as soon as possible
     * once previous events submitted with the same method are processed.
     *
     * Events submitted calling [setTimer] are executed asynchronously when they are scheduled.
     */
    fun postEvent(lifeCycleEvent: LifeCycleEvent)

    /**
     * Submit an event to be asynchronously processed
     */
    fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent)

}