package net.corda.messaging.api.config


/**
 * Strongly typed config exposed as an OSGi service to be injected anywhere it's needed.
 * Get method per config value.
 * Only expose getters.
 * Impl would retrieve values from an in memory object.
 * Values set to in-memory object/cache by separate service which reads from the  compacted log topic.
 */
interface ConfigService {

    fun getSomeValue () : String

    fun getSomeOtherValue () : String

    fun getSomeOtherValues () : List<String>

    fun getSomeMoreValues () : List<Long>
}