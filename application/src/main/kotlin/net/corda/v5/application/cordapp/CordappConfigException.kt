package net.corda.v5.application.cordapp

/**
 * Thrown if an exception occurs in accessing or parsing cordapp configuration
 */
class CordappConfigException(msg: String, e: Throwable) : RuntimeException(msg, e)
