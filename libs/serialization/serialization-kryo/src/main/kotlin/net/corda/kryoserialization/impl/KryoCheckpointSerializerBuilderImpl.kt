package net.corda.kryoserialization.impl

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.Serializer
import net.corda.kryoserialization.DefaultKryoCustomizer
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.KryoCheckpointSerializerAdapter
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

@Component(immediate = true, service = [CheckpointSerializerBuilder::class])
class KryoCheckpointSerializerBuilderImpl @Activate constructor() : CheckpointSerializerBuilder {

    companion object {
        val log = contextLogger()
    }

    private val serializers: MutableMap<Class<*>, Serializer<*>> = mutableMapOf()
    private val singletonInstances: MutableMap<String, SingletonSerializeAsToken> = mutableMapOf()
    private var sandboxGroup: SandboxGroup? = null

    private val kryoFromQuasar = (Fiber.getFiberSerializer(false) as KryoSerializer).kryo

    override fun newCheckpointSerializer(sandboxGroup: SandboxGroup): CheckpointSerializerBuilder {
        if (this.sandboxGroup != null) {
            log.warn("Checkpoint serializer build was already in progress!  Restarting checkpoint build. " +
                    "Previous build information will be lost.")
            serializers.clear()
        }
        this.sandboxGroup = sandboxGroup
        return this
    }

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
                throw CordaRuntimeException("Custom serializers for public keys are not allowed")
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
        // The exception doesn't quite match what we're checking, but this will only exist here if
        // newCheckpointSerializer was called.
        val sandboxGroup = sandboxGroup ?:
            throw CordaRuntimeException("Cannot build a Checkpoint Serializer without first calling " +
                    "`newCheckpointSerializer`.")

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
            this.sandboxGroup = null
            serializers.clear()
            singletonInstances.clear()
        }
    }
}
