@file:JvmName("AMQPSerializerFactories")

package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.v5.serialization.SerializationContext

// Allow us to create a SerializerFactory.
interface SerializerFactoryFactory {
    fun make(context: SerializationContext): SerializerFactory
}

fun createSerializerFactoryFactory(): SerializerFactoryFactory = SerializerFactoryFactoryImpl()

open class SerializerFactoryFactoryImpl : SerializerFactoryFactory {
    override fun make(context: SerializationContext): SerializerFactory {
        return SerializerFactoryBuilder.build(context.whitelist,
                mustPreserveDataWhenEvolving = context.preventDataLoss
        )
    }
}
