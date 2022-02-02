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
import net.corda.kryoserialization.serializers.PublicKeySerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointSerializerBuilder
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import java.security.PrivateKey
import java.security.PublicKey

class KryoCheckpointSerializerBuilderImpl(
    private val keyEncodingService: KeyEncodingService,
    private val sandboxGroup: SandboxGroup
) : CheckpointSerializerBuilder {

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
            if (PrivateKey::class.java.isAssignableFrom(clazz)) {
                throw CordaKryoException("Custom serializers for private keys are not allowed")
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

        val singletonSerializeAsTokenSerializer = mapOf(
            SingletonSerializeAsToken::class.java to SingletonSerializeAsTokenSerializer(singletonInstances.toMap())
        )

        val publicKeySerializers = listOf(
            PublicKey::class.java, EdDSAPublicKey::class.java, CompositeKey::class.java,
            BCECPublicKey::class.java, BCRSAPublicKey::class.java, BCSphincs256PublicKey::class.java
        ).associateWith { PublicKeySerializer(keyEncodingService) }

        val kryo = DefaultKryoCustomizer.customize(
            kryoFromQuasar,
            serializers + singletonSerializeAsTokenSerializer + publicKeySerializers,
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
