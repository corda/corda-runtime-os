package net.corda.entityprocessor.impl.internal

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.connection.manager.DbConnectionManager
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
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
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

        private val logger = contextLogger()

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
                virtualNode,
                ctx
            )
        }
    }

    private fun initializeSandbox(
        cpks: Collection<CpkMetadata>,
        virtualNode: VirtualNodeInfo,
        ctx: MutableSandboxGroupContext): AutoCloseable {
        val serializerCloseable = putSerializer(ctx, cpks, virtualNode)
        val emfCloseable = putEntityManager(ctx, cpks, virtualNode)

        logger.info("Initialising DB Sandbox for ${virtualNode.holdingIdentity}/" +
                "${virtualNode.cpiIdentifier.name}[${virtualNode.cpiIdentifier.version}]")

        return AutoCloseable {
            logger.info("Closing DB Sandbox for ${virtualNode.holdingIdentity}/" +
                    "${virtualNode.cpiIdentifier.name}[${virtualNode.cpiIdentifier.version}]")
            serializerCloseable.close()
            emfCloseable.close()
        }
    }

    /** Add a per-sandbox serializer to this sandbox context's object store for retrieval later */
    private fun putEntityManager(
        ctx: MutableSandboxGroupContext,
        cpks: Collection<CpkMetadata>,
        virtualNode: VirtualNodeInfo
    ): AutoCloseable {
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
        val entitiesSet = JpaEntitiesSet.create(virtualNode.vaultDmlConnectionId.toString(), entityClasses)

        logger.debug("Creating EntityManagerFactory for DB Sandbox (${virtualNode.holdingIdentity}) with " +
                "${entitiesSet.persistenceUnitName}: " +
                entitiesSet.classes.joinToString(",") { it.simpleName })

        // Create the per-sandbox EMF for all the entities
        // NOTE: this is create and not getOrCreate as the dbConnectionManager does not cache vault EMFs.
        //  This is because sandboxes themselves are caches, so the EMF will be cached and cleaned up
        //  as part of that.
        val entityManagerFactory = dbConnectionManager.createEntityManagerFactory(
            virtualNode.vaultDmlConnectionId,
            entitiesSet
        )

        // Store it for future use with this sandbox.
        ctx.putObjectByKey(SANDBOX_EMF, entityManagerFactory)

        return AutoCloseable {
            logger.debug("Closing EntityManagerFactory for ${entitiesSet.persistenceUnitName}")
            entityManagerFactory.close()
        }
    }

    /** Add a per-sandbox serializer to this sandbox context's object store for retrieval later */
    private fun putSerializer(
        ctx: MutableSandboxGroupContext,
        cpks: Collection<CpkMetadata>,
        virtualNode: VirtualNodeInfo
    ): AutoCloseable {
        // This code is "borrowed" from the flow worker - it should produce the same AMQP context that was
        // used to serialize the data.  The "internalCustomSerializers" MUST be the same otherwise
        // we might struggle to deserialize some entities.
        val factory = SerializerFactoryBuilder.build(ctx.sandboxGroup)
        registerCustomSerializers(factory)  // TODO - <<<<<<<<<<  this doesn't want to be here, but makes tests pass.
        internalCustomSerializers.forEach { factory.register(it, factory) }

        val cpkSerializers = cpks.flatMap { it.cordappManifest.serializers }.toSet()
        val customSerializers = scanForCustomSerializerClasses(
            ctx.sandboxGroup,
            cpkSerializers
        )

        logger.debug("Creating SerializationService for DB Sandbox (${virtualNode.holdingIdentity}) with " +
                cpkSerializers.joinToString(",")
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
        return AutoCloseable {
            /* no op at the moment */
            logger.debug("Closing SerializationService for DB Sandbox (${virtualNode.holdingIdentity})")
        }
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
