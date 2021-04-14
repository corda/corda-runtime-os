package net.corda.messaging.api.subscription

/**
 * Interface for managing the lifecycle of objects.
 */
interface LifeCycle  {
   fun start()
   fun stop()
}

