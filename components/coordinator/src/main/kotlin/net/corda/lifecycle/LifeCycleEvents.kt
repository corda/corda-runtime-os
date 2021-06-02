package net.corda.lifecycle

interface LifeCycleEvent

object StartEvent: LifeCycleEvent

object StopEvent: LifeCycleEvent

interface TimerEvent : LifeCycleEvent {

    val key: String

}
