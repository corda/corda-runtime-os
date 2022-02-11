package net.corda.schema.configuration.provider

/**
 * Exception thrown when requested config schema is not available.
 */
class ConfigSchemaException(msg: String) : RuntimeException(msg)