package net.corda.schema.configuration

/**
 * Configuration keys to access public parts of the configuration under the corda.messaging key
 */
object MessagingKeys {

    /**
     * Configuration for connecting to the underlying message bus.
     */
    const val BUS = "bus"

    /**
     * Subscription related configuration.
     */
    const val SUBSCRIPTION = "subscription"

    /**
     * Publisher related configuration.
     */
    const val PUBLISHER = "publisher"
}