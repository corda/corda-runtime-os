package com.r3.corda.notary.plugin.common

/**
 * An exception that is thrown by the plugin server but will never be visible outside of the plugin context.
 */
class InternalNotaryException(msg: String) : RuntimeException(msg)
