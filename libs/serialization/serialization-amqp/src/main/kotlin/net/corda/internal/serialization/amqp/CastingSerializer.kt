package net.corda.internal.serialization.amqp

interface CastingSerializer<out T, out R> : AMQPSerializer<T> {
    val actualType: Class<out R>
}
