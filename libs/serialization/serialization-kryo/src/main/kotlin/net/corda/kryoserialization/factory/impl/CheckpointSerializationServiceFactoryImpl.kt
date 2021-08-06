package net.corda.kryoserialization.factory.impl

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import net.corda.cipher.suite.internal.BasicHashingServiceImpl
import net.corda.kryoserialization.AlwaysAcceptEncodingWhitelist
import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.kryoserialization.CheckpointSerializationService
import net.corda.kryoserialization.CheckpointSerializer
import net.corda.kryoserialization.KryoCheckpointSerializerBuilder
import net.corda.kryoserialization.QuasarWhitelist
import net.corda.kryoserialization.SerializationDefaults
import net.corda.kryoserialization.factory.CheckpointSerializationServiceFactory
import net.corda.kryoserialization.impl.CheckpointSerializationContextImpl
import net.corda.kryoserialization.impl.CheckpointSerializationServiceImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serializers.PrivateKeySerializer
import net.corda.serializers.PublicKeySerializer
import net.corda.v5.crypto.CompositeKey
import org.bouncycastle.jcajce.interfaces.EdDSAPrivateKey
import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.osgi.service.component.annotations.Component
import java.security.PrivateKey
import java.security.PublicKey

@Component(immediate = true, service = [CheckpointSerializationServiceFactory::class])
class CheckpointSerializationServiceFactoryImpl : CheckpointSerializationServiceFactory {

    override fun createCheckpointSerializationService(sandboxGroup: SandboxGroup): CheckpointSerializationService {
        return CheckpointSerializationServiceImpl(createCheckpointContext(sandboxGroup), createCheckpointSerializer())
    }

    private fun createCheckpointContext(sandboxGroup: SandboxGroup): CheckpointSerializationContext {
        val context = CheckpointSerializationContextImpl(
            SerializationDefaults.javaClass.classLoader,
            QuasarWhitelist,
            emptyMap(),
            true,
            null,
            AlwaysAcceptEncodingWhitelist
        )
        context.withSandboxGroup(sandboxGroup)
        return context
    }

    private fun createCheckpointSerializer(): CheckpointSerializer {
        val kryoFromQuasar = { (Fiber.getFiberSerializer(false) as KryoSerializer).kryo }
        val hashingService = BasicHashingServiceImpl()
        val serializerBuilder = KryoCheckpointSerializerBuilder(kryoFromQuasar, hashingService)
        serializerBuilder.addSerializerForClasses(
            listOf(
                PublicKey::class.java, EdDSAPublicKey::class.java, CompositeKey::class.java,
                BCECPublicKey::class.java, BCRSAPublicKey::class.java, BCSphincs256PublicKey::class.java
            ), PublicKeySerializer()
        )
        serializerBuilder.addSerializerForClasses(
            listOf(
                PrivateKey::class.java, EdDSAPrivateKey::class.java, BCECPrivateKey::class.java,
                BCRSAPrivateCrtKey::class.java, BCSphincs256PrivateKey::class.java
            ), PrivateKeySerializer()
        )
        return serializerBuilder.build()
    }
}