package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.CustomSerializer

/**
 * A serializer for [StringBuffer].
 */
object StringBufferSerializer : CustomSerializer.ToString<StringBuffer>(StringBuffer::class.java)