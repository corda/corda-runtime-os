package net.corda.v5.application.flows

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.MissingSerializerException

/**
 * Thrown whenever a flow result cannot be serialized when attempting to save it in the database
 */
class ResultSerializationException private constructor(message: String?) : CordaRuntimeException(message) {
    constructor(e: MissingSerializerException): this(e.message)
}