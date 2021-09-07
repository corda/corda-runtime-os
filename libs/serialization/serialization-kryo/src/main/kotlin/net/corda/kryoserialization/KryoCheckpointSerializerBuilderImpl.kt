package net.corda.kryoserialization

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.impl.CheckpointSerializationContextImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.serializers.PrivateKeySerializer
import net.corda.serializers.PublicKeySerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.BasicHashingService
import net.corda.v5.crypto.CompositeKey
import org.bouncycastle.jcajce.interfaces.EdDSAPrivateKey
import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PrivateKey
import java.security.PublicKey

@Component(immediate = true, service = [CheckpointSerializerBuilder::class])
class KryoCheckpointSerializerBuilderImpl @Activate constructor(
    @Reference
    private val classInfoService: ClassInfoService,
    @Reference
    private val hashingService: BasicHashingService
) : CheckpointSerializerBuilder {

    companion object {
        val log = contextLogger()
    }

    private val serializers: MutableMap<Class<*>, CheckpointInternalCustomSerializer<*>> = mutableMapOf()
    private var checkpointContext: CheckpointSerializationContext? = null

    private val kryoFromQuasar = (Fiber.getFiberSerializer(false) as KryoSerializer).kryo

    override fun newCheckpointSerializer(sandboxGroup: SandboxGroup): CheckpointSerializerBuilder {
        if (checkpointContext != null) {
            log.warn("Checkpoint serializer build was already in progress!  Restarting checkpoint build. " +
                    "Previous build information will be lost.")
            serializers.clear()
        }
        checkpointContext = createCheckpointContext(sandboxGroup)
        return this
    }

    override fun addSerializer(
        clazz: Class<*>,
        serializer: CheckpointInternalCustomSerializer<*>
    ): CheckpointSerializerBuilder {
        if (PublicKey::class.java.isAssignableFrom(clazz)) {
            throw CordaRuntimeException("Custom serializers for public keys are not allowed")
        }
        serializers += mapOf(clazz to serializer)
        return this
    }

    override fun addSerializerForClasses(
        classes: List<Class<*>>,
        serializer: CheckpointInternalCustomSerializer<*>
    ): CheckpointSerializerBuilder {
        serializers += classes.associateWith { serializer }
        return this
    }

    override fun build(): KryoCheckpointSerializer {
        // The exception doesn't quite match what we're checking, but this will only exist here if
        // newCheckpointSerializer was called.
        val checkpointContext = checkpointContext ?:
            throw CordaRuntimeException("Cannot build a Checkpoint Serializer without first calling " +
                    "`newCheckpointSerializer`.")

        // These probably should go somewhere else, but they're meant to be right before building as,
        // for security purposes, we don't want someone overriding them
        val publicKeyClasses = listOf(
            PublicKey::class.java, EdDSAPublicKey::class.java, CompositeKey::class.java,
            BCECPublicKey::class.java, BCRSAPublicKey::class.java, BCSphincs256PublicKey::class.java
        )
        val privateKeyClasses = listOf(
            PrivateKey::class.java, EdDSAPrivateKey::class.java, BCECPrivateKey::class.java,
            BCRSAPrivateCrtKey::class.java, BCSphincs256PrivateKey::class.java
        )

        return KryoCheckpointSerializer(
            kryoFromQuasar,
            serializers +
                    publicKeyClasses.associateWith { PublicKeySerializer() } +
                    privateKeyClasses.associateWith { PrivateKeySerializer() },
            hashingService,
            checkpointContext
        ).also {
            // Clear the builder state
            this.checkpointContext = null
            serializers.clear()
        }
    }

    private fun createCheckpointContext(sandboxGroup: SandboxGroup): CheckpointSerializationContext {
        return CheckpointSerializationContextImpl(
            null,
            KryoCheckpointSerializer::class.java.classLoader,
            emptyMap(),
            true,
            classInfoService,
            sandboxGroup
        )
    }

}
