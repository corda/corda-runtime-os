package net.corda.kryoserialization.impl

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.Serializer
import net.corda.kryoserialization.CordaKryoException
import net.corda.kryoserialization.DefaultKryoCustomizer
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.KryoCheckpointSerializerAdapter
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.security.PublicKey

class KryoCheckpointSerializerBuilderImpl(
    private val sandboxGroup: SandboxGroup
) : CheckpointSerializerBuilder {

    companion object {
        val log = contextLogger()
    }

    private val serializers: MutableMap<Class<*>, Serializer<*>> = mutableMapOf()
    private val singletonInstances: MutableMap<String, SingletonSerializeAsToken> = mutableMapOf()

    private val kryoFromQuasar = (Fiber.getFiberSerializer(false) as KryoSerializer).kryo

    override fun addSerializer(
        clazz: Class<*>,
        serializer: CheckpointInternalCustomSerializer<*>
    ): CheckpointSerializerBuilder = addSerializerForClasses(listOf(clazz), serializer)

    override fun addSerializerForClasses(
        classes: List<Class<*>>,
        serializer: CheckpointInternalCustomSerializer<*>
    ): CheckpointSerializerBuilder {
        for (clazz in classes) {
            if (PublicKey::class.java.isAssignableFrom(clazz)) {
                throw CordaKryoException("Custom serializers for public keys are not allowed")
            }
            serializers[clazz] = KryoCheckpointSerializerAdapter(serializer).adapt()
        }
        return this
    }

    override fun addSingletonSerializableInstances(
        instances: Set<SingletonSerializeAsToken>
    ): CheckpointSerializerBuilder {
        singletonInstances.putAll(instances.associateBy { it.tokenName })
        return this
    }

    override fun build(): KryoCheckpointSerializer {
        val classResolver = CordaClassResolver(sandboxGroup)
        val classSerializer = ClassSerializer(sandboxGroup)

        val singletonSerializeAsTokenSerializer = SingletonSerializeAsTokenSerializer(singletonInstances.toMap())

        val kryo = DefaultKryoCustomizer.customize(
            kryoFromQuasar,
            serializers + mapOf(SingletonSerializeAsToken::class.java to singletonSerializeAsTokenSerializer),
            classResolver,
            classSerializer,
        )

        return KryoCheckpointSerializer(kryo).also {
            // Clear the builder state
            serializers.clear()
            singletonInstances.clear()
        }
    }
}
