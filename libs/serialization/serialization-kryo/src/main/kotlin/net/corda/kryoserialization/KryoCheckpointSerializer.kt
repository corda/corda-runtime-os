package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.crypto.BasicHashingService
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import java.io.ByteArrayInputStream

class KryoCheckpointSerializer(
    kryoFromQuasar: Kryo,
    private val serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>>,
    private val hashingService: BasicHashingService,
    private val checkpointContext: CheckpointSerializationContext
) : CheckpointSerializer {

    // Should this be lazy?
    private val kryo = getKryo(kryoFromQuasar)

    private fun getKryo(kryoFromQuasar: Kryo): Kryo {
        return kryoFromQuasar.apply {

            classLoader = FrameworkUtil.getBundle(this::class.java)?.adapt(BundleWiring::class.java)?.classLoader
                ?: this::class.java.classLoader

            val classResolver = CordaClassResolver(
                checkpointContext.classInfoService,
                checkpointContext.sandboxGroup,
                hashingService
            )
            classResolver.setKryo(this)

            // The ClassResolver can only be set in the Kryo constructor and Quasar doesn't
            // provide us with a way of doing that
            Kryo::class.java.getDeclaredField("classResolver").apply {
                isAccessible = true
            }.set(this, classResolver)

            // don't allow overriding the public key serializer for checkpointing
            DefaultKryoCustomizer.customize(this)
            addDefaultSerializer(
                Class::class.java,
                ClassSerializer(checkpointContext.classInfoService, checkpointContext.sandboxGroup, hashingService)
            )

            //Add external serializers
            for (serializer in serializers) {
                addDefaultSerializer(serializer.key, KryoCheckpointSerializerAdapter(serializer.value).adapt())
            }
        }
    }

    override fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: Class<T>,
    ): T {
        val payload = kryoMagic.consume(bytes)
            ?: throw KryoException("Serialized bytes header does not match expected format.")
        return kryoInput(ByteArrayInputStream(payload)) {
            uncheckedCast(kryo.readClassAndObject(this))
        }
    }

    override fun <T : Any> serialize(obj: T): ByteArray {
        return kryoOutput {
            kryoMagic.writeTo(this)
            kryo.writeClassAndObject(this, obj)
        }
    }
}

