package net.corda.messagebus.kafka.config

import net.corda.libs.configuration.SmartConfig
import java.util.Properties

/**
 * Resolve a Kafka bus configuration against the enforced and default configurations provided by the library.
 */
interface ConfigResolver {

    /**
     * Resolve the provided configuration and return a valid set of Kafka properties suitable for the given role.
     *
     * @param config The supplied message bus configuration. Must match the schema used in the defaults and enforced
     *               config files included with this library
     * @param role The role to be configured. This is a path representing the object type being created at the patterns
     *             layer and a description of which consumer or producer is requested. For example, the event consumer
     *             for the state and event pattern has role "subscription.stateAndEvent.eventConsumer".
     */
    fun resolve(config: SmartConfig, role: String): Properties
}