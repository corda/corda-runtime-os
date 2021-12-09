package net.corda.virtualnode

import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle

/**
 * Simple interface to handle configuration 'events'
 */
interface ConfigPublisher {
    /** Config has been published, and all components are now [LifecycleStatus.UP] */
    fun ready()

    /** Process is stopping */
    fun done()

    /**
     * Called when we have published configuration to Kafka.
     *
     * You must register a [RegistrationHandle] listen to your sub-components's coordinators
     * setting their states to UP
     */
    fun configPublished()

    /** Called to publish config */
    fun publishConfig()

    /** Called to create Kafka topics, if any */
    fun createTopics()
}
