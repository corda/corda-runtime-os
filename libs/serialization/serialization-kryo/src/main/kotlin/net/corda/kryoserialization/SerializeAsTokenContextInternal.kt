package net.corda.kryoserialization

import net.corda.v5.serialization.SerializationToken
import net.corda.v5.serialization.SerializeAsTokenContext
import net.corda.v5.serialization.SingletonSerializeAsToken

interface SerializeAsTokenContextInternal : SerializeAsTokenContext {

    fun toSingletonToken(serializeAsToken: SingletonSerializeAsToken): SerializationToken
}