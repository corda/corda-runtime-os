package net.corda.messaging.api.subscription

/**
 * Interface for managing the lifecycle of objects.
 */
interface LifeCycle : AutoCloseable {

   fun start()
   fun stop()

   override fun close() = stop()
}

