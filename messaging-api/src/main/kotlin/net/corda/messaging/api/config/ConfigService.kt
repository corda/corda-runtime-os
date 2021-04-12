package net.corda.messaging.api.config


/**
 * Strongly typed config exposed as an OSGi service to be injected anywhere it's needed.
 * method per config value
 * Only expose getters.
 * Values set by another service written to by threads consuming from the config topic/other source
 */
interface ConfigService {

    fun getSomeValue () : String

    fun getSomeOtherValue () : String

    fun getSomeOtherValues () : List<String>

    fun getSomeMoreValues () : List<Long>
}