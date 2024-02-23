package net.corda.persistence.common.internal

import net.corda.cpk.read.CpkReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.orm.JpaEntitiesSet
import net.corda.persistence.common.EntityExtractor
import net.corda.persistence.common.EntitySandboxContextTypes.SANDBOX_EMF
import net.corda.persistence.common.EntitySandboxContextTypes.SANDBOX_TOKEN_STATE_OBSERVERS
import net.corda.persistence.common.EntitySandboxContextTypes.SANDBOX_TOKEN_STATE_OBSERVERS_V2
import net.corda.persistence.common.EntitySandboxService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
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
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.observer.UtxoTokenTransactionStateObserver
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
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
@Component(service = [EntitySandboxService::class])
class EntitySandboxServiceImpl @Activate constructor(
    @Reference
    private val sandboxService: SandboxGroupContextComponent,
    @Reference(service = CpkReadService::class)
    private val cpkReadService: CpkReadService,
    @Reference
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference
    private val dbConnectionManager: DbConnectionManager
) : EntitySandboxService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun get(holdingIdentity: HoldingIdentity, cpkFileHashes: Set<SecureHash>): SandboxGroupContext {
        // We're throwing internal exceptions so that we can relay some information back to the flow worker
        // on how to proceed with any request to us that fails.
        val virtualNode = virtualNodeInfoService.get(holdingIdentity)
            ?: throw VirtualNodeException("Could not get virtual node for $holdingIdentity")

        if (!sandboxService.hasCpks(cpkFileHashes))
            throw CpkNotAvailableException("CPKs not available (yet): $cpkFileHashes")

        val cpks = cpkFileHashes.mapNotNull { cpkReadService.get(it)?.metadata }

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
        val namedQueryFactories = sandboxService.registerLedgerNamedQueryFactories(ctx)
        val contractStateJsonFactories = sandboxService.registerLedgerContractStateJsonFactories(ctx)
        val emfCloseable = putEntityManager(ctx, cpks, virtualNode)
        putTokenStateObservers(ctx, cpks)

        val jsonDeserializers = sandboxService.registerCustomJsonDeserializers(ctx)
        val jsonSerializers = sandboxService.registerCustomJsonSerializers(ctx)

        // Instruct all CustomMetadataConsumers to accept their metadata.
        sandboxService.acceptCustomMetadata(ctx)

        if (logger.isInfoEnabled) {
            logger.info(
                "Initialising DB Sandbox for {}/{}[{}]",
                virtualNode.holdingIdentity,
                virtualNode.cpiIdentifier.name,
                virtualNode.cpiIdentifier.version
            )
        }

        return AutoCloseable {
            if (logger.isInfoEnabled) {
                logger.info(
                    "Closing DB Sandbox for {}/{}[{}]",
                    virtualNode.holdingIdentity,
                    virtualNode.cpiIdentifier.name,
                    virtualNode.cpiIdentifier.version
                )
            }
            jsonSerializers.close()
            jsonDeserializers.close()
            emfCloseable.close()
            customSerializers.close()
            customCrypto.close()
            namedQueryFactories.close()
            contractStateJsonFactories.close()
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

        if (logger.isDebugEnabled) {
            logger.debug(
                "Creating EntityManagerFactory for DB Sandbox ({}) with {}: {}",
                virtualNode.holdingIdentity,
                entitiesSet.persistenceUnitName,
                entitiesSet.classes.joinToString(",") { "${it.canonicalName}[${it.classLoader}]" }
            )
        }

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
        @Suppress("DEPRECATION")
        val tokenStateObserverMap =
            getStateObserver<net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<ContractState>>(ctx, cpks) { manifest ->
                manifest.tokenStateObservers
            }.groupBy { it.stateType }

        val tokenStateObserverMapV2 =
            getStateObserver<UtxoTokenTransactionStateObserver<ContractState>>(ctx, cpks) { manifest ->
                manifest.tokenStateObserversV2
            }.groupBy { it.stateType }

        requireSingleObserverToState(tokenStateObserverMap, tokenStateObserverMapV2)

        ctx.putObjectByKey(SANDBOX_TOKEN_STATE_OBSERVERS, singleObserverToState(tokenStateObserverMap))
        ctx.putObjectByKey(SANDBOX_TOKEN_STATE_OBSERVERS_V2, singleObserverToState(tokenStateObserverMapV2))

        genLogsDebug(tokenStateObserverMap)
        genLogsDebug(tokenStateObserverMapV2)
    }

    private inline fun <reified T : Any> getStateObserver(
        ctx: MutableSandboxGroupContext,
        cpks: Collection<CpkMetadata>,
        getObserverNameList: (CordappManifest) -> Set<String>
    ) =
        cpks
            .flatMap { getObserverNameList(it.cordappManifest) }
            .toSet()
            .mapNotNull {
                getObserverFromClassName<T>(it, ctx.sandboxGroup)
            }

    private fun <T : Any> genLogsDebug(tokenStateObserverMap: Map<Class<ContractState>, List<T>>) =
        logger.debug {
            "Registered token observers: ${
                tokenStateObserverMap.mapValues { (_, observers) ->
                    observers.map { it::class.java.name }
                }
            }"
        }

    private fun requireSingleObserverToState(
        @Suppress("DEPRECATION")
        tokenStateObserverMapV1: Map<
                Class<ContractState>,
                List<net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<ContractState>>>,
        tokenStateObserverMapV2: Map<Class<ContractState>, List<UtxoTokenTransactionStateObserver<ContractState>>>
    ) {
        val tokenStateObserverMap = merge(tokenStateObserverMapV1, tokenStateObserverMapV2)

        tokenStateObserverMap.entries.forEach { contractStateTypeToObservers ->
            val numberOfObservers = contractStateTypeToObservers.value.size
            if (numberOfObservers > 1) {
                val observerTypes = contractStateTypeToObservers.value.map { it.javaClass }
                throw IllegalStateException(
                    "More than one observer found for the contract state. " +
                            "Contract state: ${contractStateTypeToObservers.key}, observers: $observerTypes"
                )
            }
        }
    }

    private fun merge(
        @Suppress("DEPRECATION")
        map1: Map<Class<ContractState>, List<net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<ContractState>>>,
        map2: Map<Class<ContractState>, List<UtxoTokenTransactionStateObserver<ContractState>>>
    ) =
        (map1.asSequence() + map2.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten() }

    private fun <T> singleObserverToState(
        tokenStateObserverMap: Map<Class<ContractState>, List<T>>
    ): Map<Class<ContractState>, T?> =
        tokenStateObserverMap.entries.associate { contractStateTypeToObservers ->
            contractStateTypeToObservers.key to contractStateTypeToObservers.value.singleOrNull()
        }

    private inline fun <reified T : Any> getObserverFromClassName(
        className: String,
        sandboxGroup: SandboxGroup
    ): T? {
        val clazz = sandboxGroup.loadClassFromMainBundles(
            className,
            T::class.java
        )

        return try {
            @Suppress("unchecked_cast")
            clazz.getConstructor().newInstance() as T
        } catch (e: Exception) {
            logger.error(
                "The UTXO state observer '${clazz}' must implement a default public constructor.",
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

    private fun SandboxGroupContextComponent.registerLedgerNamedQueryFactories(
        sandboxGroupContext: SandboxGroupContext
    ): AutoCloseable {
        return registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.ledgerNamedQueryClasses },
            serviceMarkerType = VaultNamedQueryFactory::class.java
        )
    }

    private fun SandboxGroupContextComponent.registerLedgerContractStateJsonFactories(
        sandboxGroupContext: SandboxGroupContext
    ): AutoCloseable {
        return registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.ledgerStateJsonFactories },
            serviceMarkerType = ContractStateVaultJsonFactory::class.java
        )
    }
}
