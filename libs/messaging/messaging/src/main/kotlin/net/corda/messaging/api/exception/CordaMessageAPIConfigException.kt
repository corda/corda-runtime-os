package net.corda.messaging.api.exception

/**
 * Exception thrown when provided configuration map does not contain all required sections of configuration to set up
 * the patterns library.
 */
class CordaMessageAPIConfigException(missingKey: String) :
    RuntimeException("Could not generate a messaging patterns configuration due to missing key: $missingKey")