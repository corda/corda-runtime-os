package net.corda.lifecycle

interface LifeCycleProcessor {

    fun processEvent(lifeCycleEvent: LifeCycleEvent)

}