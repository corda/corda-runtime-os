package net.corda.kryoserialization.impl

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.ClassResolver
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.kryoserialization.CordaKryoException
import net.corda.kryoserialization.DefaultKryoCustomizer
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.KryoCheckpointSerializerAdapter
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.PublicKeySerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.kryoserialization.serializers.X500PrincipalSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointSerializerBuilder
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import java.security.PrivateKey
import java.security.PublicKey
import java.util.function.Function
import javax.security.auth.x500.X500Principal
import com.esotericsoftware.kryo.util.Pool
import net.corda.kryoserialization.KryoCheckpointSerializer1
import net.corda.kryoserialization.KryoCheckpointSerializer2
import java.util.concurrent.atomic.AtomicInteger

class KryoCheckpointSerializerBuilderImpl(
    private val keyEncodingService: KeyEncodingService,
    private val sandboxGroup: SandboxGroup,
    private val kryoFactory: Function<ClassResolver, Kryo> = Function { classResolver ->
        (Fiber.getFiberSerializer(classResolver, false) as KryoSerializer).kryo
    }
) : CheckpointSerializerBuilder {

    private val serializers: MutableMap<Class<*>, Serializer<*>> = mutableMapOf()
    private val singletonInstances: MutableMap<String, SingletonSerializeAsToken> = mutableMapOf()

    val count = AtomicInteger(0)

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
//        val classResolver = CordaClassResolver(sandboxGroup)
//        val classSerializer = ClassSerializer(sandboxGroup)

        val publicKeySerializers = listOf(
            PublicKey::class.java, EdDSAPublicKey::class.java, CompositeKey::class.java,
            BCECPublicKey::class.java, BCRSAPublicKey::class.java, BCSphincs256PublicKey::class.java
        ).associateWith { PublicKeySerializer(keyEncodingService) }

        val otherCustomSerializers = mapOf(
            SingletonSerializeAsToken::class.java to SingletonSerializeAsTokenSerializer(singletonInstances.toMap()),
            X500Principal::class.java to X500PrincipalSerializer()
        )

//        val kryo = DefaultKryoCustomizer.customize(
//            kryoFactory.apply(classResolver),
//            serializers + publicKeySerializers + otherCustomSerializers,
//            classSerializer
//        )
//
//        return KryoCheckpointSerializer1(kryo).also {
//            // Clear the builder state
//            serializers.clear()
//            singletonInstances.clear()
//        }

        // forced kryo serialization to be single threaded afaik by setting `maximumCapacity` to 1
        // I was still getting errors when the pool was larger
        val pool = object : Pool<MyKryo>(true, false, 4) {
            override fun create(): MyKryo {
                this.peak
                val classResolver = CordaClassResolver(sandboxGroup)
                val classSerializer = ClassSerializer(sandboxGroup)
                return MyKryo(count.getAndIncrement(), DefaultKryoCustomizer.customize(
                    kryoFactory.apply(classResolver),
                    serializers + publicKeySerializers + otherCustomSerializers,
                    classSerializer
                ).also {
                    classResolver.setKryo(it)
                }
                )
            }
        }

        return KryoCheckpointSerializer2(pool).also {
            // Clear the builder state
//            serializers.clear()
//            singletonInstances.clear()
        }
    }
}

class MyKryo(val id: Int, val kryo: Kryo) {
    override fun toString(): String {
        return "MyKryo(count=$id)"
    }
}
