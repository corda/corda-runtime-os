package net.corda.entityprocessor.impl.internal

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes.SANDBOX_EMF
import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes.SANDBOX_SERIALIZER
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl.Companion.INTERNAL_CUSTOM_SERIALIZERS
import net.corda.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.libs.packaging.CpkMetadata
import net.corda.orm.JpaEntitiesSet
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy
import java.util.UUID

/** It's a sandbox service, that's internal to this component!
 *
 * It gets/creates a per-sandbox with:
 *
 *   * serializer
 *   * entity manager factory
 *
 *
 * */
@Suppress("LongParameterList")
@Component(
    service = [EntitySandboxService::class],
    reference = [
        Reference(
            name = INTERNAL_CUSTOM_SERIALIZERS,
            service = InternalCustomSerializer::class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
        )
    ]
)
class EntitySandboxServiceImpl @Activate constructor (
    @Reference
    private val sandboxService: SandboxGroupContextComponent,
    @Reference
    private val cpiInfoService: CpiInfoReadService,
    @Reference
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference
    private val dbConnectionManager: DbConnectionManager,
    private val componentContext: ComponentContext
) : EntitySandboxService {
    companion object {
        const val INTERNAL_CUSTOM_SERIALIZERS = "internalCustomSerializers"

        private fun <T> ComponentContext.fetchServices(refName: String): List<T> {
            @Suppress("unchecked_cast")
            return (locateServices(refName) as? Array<T>)?.toList() ?: emptyList()
        }
    }

    private val internalCustomSerializers
        get() = componentContext.fetchServices<InternalCustomSerializer<out Any>>(INTERNAL_CUSTOM_SERIALIZERS)

    override fun get(holdingIdentity: HoldingIdentity): SandboxGroupContext {
        val virtualNode = virtualNodeInfoService.get(holdingIdentity)
            ?: throw CordaRuntimeException("Could not get virtual node for $holdingIdentity")

        val cpks = cpiInfoService.get(virtualNode.cpiIdentifier)?.cpksMetadata
            ?: throw CordaRuntimeException("Could not get list of CPKs for ${virtualNode.cpiIdentifier}")

        return sandboxService.getOrCreate(getVirtualNodeContext(virtualNode, cpks)) { _, ctx ->
            initializeSandbox(
                cpks,
                ctx
            )
        }
    }

    private fun initializeSandbox(cpks: Collection<CpkMetadata>, ctx: MutableSandboxGroupContext): AutoCloseable {
        val serializerCloseable = putSerializer(ctx, cpks)
        val emfCloseable = putEntityManager(ctx, cpks)

        return AutoCloseable {
            serializerCloseable.close()
            emfCloseable.close()
        }
    }

    /** Add a per-sandbox serializer to this sandbox context's object store for retrieval later */
    private fun putEntityManager(ctx: MutableSandboxGroupContext, cpks: Collection<CpkMetadata>): AutoCloseable {
        // Get all the entity class names from the cpk metadata, and then map to their types
        // This bit is quite important - we've bnd-scanned the **CPKS** (not jars!) and written the list
        // of classes annotated with @Entity to the CPK manifest.  We now use them and convert to types
        // so that we can correctly construct an entity manager factory per sandbox.

        // TODO:  platform entities as well?
        val entityClasses = EntityExtractor.getEntityClassNames(cpks).map {
            try {
                ctx.sandboxGroup.loadClassFromMainBundles(it)
            } catch (e: SandboxException) {
                throw e // TODO: what do we do here?
            }
        }.toSet()

        // We now have the collection of class types, from the CPKs, with their *own* classloaders (i.e. osgi).

        // Create the JPA entity set to pass into the EMF
        // TODO:  first parameter name
        val entitiesSet = JpaEntitiesSet.create(UUID.randomUUID().toString(), entityClasses)

        // TODO: CHECK THAT WE USE A SPECIFIC DATABASE USER FOR THIS CPI / SANDBOX

        // Create the per-sandbox EMF for all the entities
        val entityManagerFactory = dbConnectionManager.getOrCreateEntityManagerFactory(
            UUID.randomUUID().toString(), // ???
            DbPrivilege.DDL, // ???
            entitiesSet
        )

        // Store it for future use with this sandbox.
        ctx.putObjectByKey(SANDBOX_EMF, entityManagerFactory)

        return AutoCloseable { entityManagerFactory.close() }
    }

    /** Add a per-sandbox serializer to this sandbox context's object store for retrieval later */
    private fun putSerializer(ctx: MutableSandboxGroupContext, cpks: Collection<CpkMetadata>): AutoCloseable {
        // This code is "borrowed" from the flow worker - it should produce the same AMQP context that was
        // used to serialize the data.  The "internalCustomSerializers" MUST be the same otherwise
        // we might struggle to deserialize some entities.
        val factory = SerializerFactoryBuilder.build(ctx.sandboxGroup)
        registerCustomSerializers(factory)  // TODO - <<<<<<<<<<  this doesn't want to be here, but makes tests pass.
        internalCustomSerializers.forEach { factory.register(it, factory) }

        val customSerializers = scanForCustomSerializerClasses(
            ctx.sandboxGroup,
            cpks.flatMap { it.cordappManifest.serializers }.toSet()
        )

        customSerializers.forEach { factory.registerExternal(it, factory) }

        val serializationOutput = SerializationOutput(factory)
        val deserializationInput = DeserializationInput(factory)

        val serializationService = SerializationServiceImpl(
            serializationOutput,
            deserializationInput,
            AMQP_P2P_CONTEXT.withSandboxGroup(ctx.sandboxGroup)
        )

        ctx.putObjectByKey(SANDBOX_SERIALIZER, serializationService)
        return AutoCloseable { /* no op at the moment */ }
    }

    private fun scanForCustomSerializerClasses(
        sandboxGroup: SandboxGroup,
        serializerClassNames: Set<String>
    ): List<SerializationCustomSerializer<*, *>> {
        return serializerClassNames.map {
            sandboxGroup.loadClassFromMainBundles(
                it,
                SerializationCustomSerializer::class.java
            ).getConstructor().newInstance()
        }
    }

    /** NOTE THE SANDBOX GROUP TYPE HERE */
    private fun getVirtualNodeContext(virtualNode: VirtualNodeInfo, cpks: Collection<CpkMetadata>) =
        VirtualNodeContext(
            virtualNode.holdingIdentity,
            cpks.mapTo(LinkedHashSet(), CpkMetadata::cpkId),
            SandboxGroupType.PERSISTENCE, // TODO - we're still not doing *anything* per sandbox type
            SingletonSerializeAsToken::class.java,
            null
        )
}
