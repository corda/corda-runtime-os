package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.addToWhitelist
import java.io.NotSerializableException
import javax.annotation.concurrent.ThreadSafe

data class SerializationSchemas(val schema: Schema, val transforms: TransformsSchema)

/**
 * Factory of serializers designed to be shared across threads and invocations.
 *
 * @property evolutionSerializerProvider controls how evolution serializers are generated by the factory. The normal
 * use case is an [EvolutionSerializer] type is returned. However, in some scenarios, primarily testing, this
 * can be altered to fit the requirements of the test.
 * @property onlyCustomSerializers used for testing, when set will cause the factory to throw a
 * [NotSerializableException] if it cannot find a registered custom serializer for a given type
 */
@ThreadSafe
interface SerializerFactory : LocalSerializerFactory, RemoteSerializerFactory, CustomSerializerRegistry

class ComposedSerializerFactory(
        private val localSerializerFactory: LocalSerializerFactory,
        private val remoteSerializerFactory: RemoteSerializerFactory,
        private val customSerializerRegistry: CachingCustomSerializerRegistry
) : SerializerFactory,
        LocalSerializerFactory by localSerializerFactory,
        RemoteSerializerFactory by remoteSerializerFactory,
        CustomSerializerRegistry by customSerializerRegistry {

        override val customSerializerNames: List<String>
                get() = customSerializerRegistry.customSerializerNames

        override fun registerExternal(customSerializer: CorDappCustomSerializer) {
                customSerializerRegistry.registerExternal(customSerializer)
                addToWhitelist(listOf(customSerializer.proxyType.asClass()))
        }
}