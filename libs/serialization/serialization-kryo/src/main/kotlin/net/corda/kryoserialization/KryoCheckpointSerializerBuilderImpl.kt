package net.corda.kryoserialization

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.BasicHashingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
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
        val sandboxGroup = sandboxGroup ?:
            throw CordaRuntimeException("Cannot build a Checkpoint Serializer without first calling " +
                    "`newCheckpointSerializer`.")

        val classResolver = CordaClassResolver(
            classInfoService,
            sandboxGroup,
            hashingService
        )

        val classSerializer = ClassSerializer(
            classInfoService,
            sandboxGroup,
            hashingService
        )

        val kryo = DefaultKryoCustomizer.customize(
            kryoFromQuasar,
            serializers,
            classResolver,
            classSerializer
        )

        return KryoCheckpointSerializer(kryo).also {
            // Clear the builder state
            this.sandboxGroup = null
            serializers.clear()
        }
    }
}
