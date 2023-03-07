package net.corda.persistence.common.internal

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.orm.JpaEntitiesSet
import net.corda.persistence.common.EntityExtractor
import net.corda.persistence.common.EntitySandboxContextTypes.SANDBOX_EMF
import net.corda.persistence.common.EntitySandboxContextTypes.SANDBOX_TOKEN_STATE_OBSERVERS
import net.corda.persistence.common.EntitySandboxService
import net.corda.sandbox.SandboxException
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.RequireSandboxJSON
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.registerCordappCustomSerializers
import net.corda.sandboxgroupcontext.service.registerCustomCryptography
import net.corda.sandboxgroupcontext.service.registerCustomJsonDeserializers
import net.corda.sandboxgroupcontext.service.registerCustomJsonSerializers
import net.corda.utilities.debug
import net.corda.v5.ledger.common.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * This is a sandbox service that is internal to this component.
 *
 * It gets/creates a sandbox with a per-sandbox:
 *
 *   * serializer
 *   * entity manager factory
 *
 */
@Suppress("LongParameterList")
@RequireSandboxAMQP
@RequireSandboxJSON
@Component(service = [ EntitySandboxService::class ])
class EntitySandboxServiceImpl @Activate constructor(
    @Reference
    private val sandboxService: SandboxGroupContextComponent,
    @Reference
    private val cpiInfoService: CpiInfoReadService,
    @Reference
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference
    private val dbConnectionManager: DbConnectionManager
) : EntitySandboxService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun get(holdingIdentity: HoldingIdentity): SandboxGroupContext {
        // We're throwing internal exceptions so that we can relay some information back to the flow worker
        // on how to proceed with any request to us that fails.
        val virtualNode = virtualNodeInfoService.get(holdingIdentity)
            ?: throw VirtualNodeException("Could not get virtual node for $holdingIdentity")

        val cpks = cpiInfoService.get(virtualNode.cpiIdentifier)?.cpksMetadata
            ?: throw VirtualNodeException("Could not get list of CPKs for ${virtualNode.cpiIdentifier}")

        val cpkIds = cpks.mapTo(mutableSetOf(), CpkMetadata::fileChecksum)
        if (!sandboxService.hasCpks(cpkIds))
            throw CpkNotAvailableException("CPKs not available (yet): $cpkIds")

        return sandboxService.getOrCreate(getVirtualNodeContext(virtualNode, cpks)) { _, ctx ->
            initializeSandbox(cpks, virtualNode, ctx)
        }
    }

    private fun initializeSandbox(
        cpks: Collection<CpkMetadata>,
        virtualNode: VirtualNodeInfo,
        ctx: MutableSandboxGroupContext
    ): AutoCloseable {
        val customCrypto = sandboxService.registerCustomCryptography(ctx)
        val customSerializers = sandboxService.registerCordappCustomSerializers(ctx)
        val customQueryFactories = sandboxService.registerLedgerCustomQueryFactories(ctx)
        val emfCloseable = putEntityManager(ctx, cpks, virtualNode)
        putTokenStateObservers(ctx, cpks)

        val jsonDeserializers = sandboxService.registerCustomJsonDeserializers(ctx)
        val jsonSerializers = sandboxService.registerCustomJsonSerializers(ctx)

        // Instruct all CustomMetadataConsumers to accept their metadata.
        sandboxService.acceptCustomMetadata(ctx)

        logger.info("Initialising DB Sandbox for {}/{}[{}]",
            virtualNode.holdingIdentity,
            virtualNode.cpiIdentifier.name,
            virtualNode.cpiIdentifier.version
        )

        return AutoCloseable {
            logger.info("Closing DB Sandbox for {}/{}[{}]",
                virtualNode.holdingIdentity,
                virtualNode.cpiIdentifier.name,
                virtualNode.cpiIdentifier.version
            )
            jsonSerializers.close()
            jsonDeserializers.close()
            emfCloseable.close()
            customSerializers.close()
            customCrypto.close()
            customQueryFactories.close()
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
        // of classes annotated with @Entity and @CordaSerializable to the CPK manifest.
        // We now use them and convert them to types so that we can correctly construct an
        // entity manager factory per sandbox.

        // TODO - add general vault entities
        val entityClasses = EntityExtractor.getEntityClassNames(cpks).map {
            try {
                ctx.sandboxGroup.loadClassFromMainBundles(it)
            } catch (e: SandboxException) {
                throw e
            }
        }.toSet()

        // We now have the collection of class types, from the CPKs, with their *own* classloaders (i.e. osgi).

        // Create the JPA entity set to pass into the EMF
        val entitiesSet = JpaEntitiesSet.create(virtualNode.vaultDmlConnectionId.toString(), entityClasses)

        logger.info("Creating EntityManagerFactory for DB Sandbox ({}) with {}: {}",
            virtualNode.holdingIdentity,
            entitiesSet.persistenceUnitName,
            entitiesSet.classes.joinToString(",") { "${it.canonicalName}[${it.classLoader}]" }
        )

        // Create the per-sandbox EMF for all the entities
        // NOTE: this is create and not getOrCreate as the dbConnectionManager does not cache vault EMFs.
        // This is because sandboxes themselves are caches, so the EMF will be cached and cleaned up
        // as part of that.
        val entityManagerFactory = dbConnectionManager.createEntityManagerFactory(
            virtualNode.vaultDmlConnectionId,
            entitiesSet
        )

        // Store it for future use with this sandbox.
        ctx.putObjectByKey(SANDBOX_EMF, entityManagerFactory)

        return AutoCloseable {
            logger.debug("Closing EntityManagerFactory for {}", entitiesSet.persistenceUnitName)
            entityManagerFactory.close()
        }
    }

    private fun putTokenStateObservers(
        ctx: MutableSandboxGroupContext,
        cpks: Collection<CpkMetadata>
    ) {
        val tokenStateObserverMap = cpks
            .flatMap { it.cordappManifest.tokenStateObservers }
            .toSet()
            .mapNotNull { getObserverFromClassName(it, ctx) }
            .groupBy { it.stateType }
        ctx.putObjectByKey(SANDBOX_TOKEN_STATE_OBSERVERS, tokenStateObserverMap)
        logger.debug {
            "Registered token observers: ${tokenStateObserverMap.mapValues { (_, observers) ->
                observers.map { it::class.java.name }}
            }"
        }
    }

    private fun getObserverFromClassName(
        className: String,
        ctx: MutableSandboxGroupContext
    ): UtxoLedgerTokenStateObserver<ContractState>? {
        val clazz = ctx.sandboxGroup.loadClassFromMainBundles(
            className,
            UtxoLedgerTokenStateObserver::class.java
        )

        return try {
            @Suppress("unchecked_cast")
            clazz.getConstructor().newInstance() as UtxoLedgerTokenStateObserver<ContractState>
        } catch (e: Exception) {
            logger.error(
                "The UtxoLedgerTokenStateObserver '${clazz}' must implement a default public constructor.",
                e
            )
            null
        }
    }

    /** NOTE THE SANDBOX GROUP TYPE HERE */
    private fun getVirtualNodeContext(virtualNode: VirtualNodeInfo, cpks: Collection<CpkMetadata>) =
        VirtualNodeContext(
            virtualNode.holdingIdentity,
            cpks.filter(CpkMetadata::isContractCpk).mapTo(linkedSetOf(), CpkMetadata::fileChecksum),
            SandboxGroupType.PERSISTENCE,
            null
        )

    private fun SandboxGroupContextComponent.registerLedgerCustomQueryFactories(
        sandboxGroupContext: SandboxGroupContext
    ): AutoCloseable {
        return registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.ledgerCustomQueryClasses },
            serviceMarkerType = VaultNamedQueryFactory::class.java
        )
    }
}
