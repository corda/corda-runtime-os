package net.corda.processors.db.internal.reconcile.db

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.certificates.toPemCertificateChain
import net.corda.membership.datamodel.HostedIdentityEntity
import net.corda.membership.datamodel.HostedIdentitySessionKeyInfoEntity
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.reconciliation.VersionedRecord
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.debug
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.stream.Stream
import javax.persistence.EntityManager
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.client.ReconcilerCryptoOpsClient
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.orm.JpaEntitiesRegistry

@Suppress("LongParameterList")
class HostedIdentityReconciler(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val reconcilerFactory: ReconcilerFactory,
    private val reconcilerReader: ReconcilerReader<String, HostedIdentityEntry>,
    private val reconcilerWriter: ReconcilerWriter<String, HostedIdentityEntry>,
    private val reconcilerCryptoOpsClient: ReconcilerCryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    ) : ReconcilerWrapper {
    private companion object {
        private const val CACHE_SIZE = 1024L
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        )
    }

    @VisibleForTesting
    internal var dbReconciler: DbReconcilerReader<String, HostedIdentityEntry>? = null

    @VisibleForTesting
    internal var reconciler: Reconciler? = null

    private val reconciliationContextFactory = {
        Stream.of(ClusterReconciliationContext(dbConnectionManager))
    }

    private data class KeyLookup(
        val tenantId: String,
        val sessionKeyId: ShortHash,
    )

    private val cachedKeys: Cache<KeyLookup, String> = CacheFactoryImpl().build(
        "Hosted-Identity-Reconciler-Cached-Keys",
        Caffeine.newBuilder().maximumSize(CACHE_SIZE)
    )

    override fun updateInterval(intervalMillis: Long) {
        logger.debug { "Config reconciliation interval set to $intervalMillis ms" }

        if (dbReconciler == null) {
            dbReconciler =
                DbReconcilerReader(
                    coordinatorFactory,
                    String::class.java,
                    HostedIdentityEntry::class.java,
                    dependencies,
                    reconciliationContextFactory,
                    ::getAllHostedIdentities
                ).also {
                    it.start()
                }
        }

        if (reconciler == null) {
            reconciler = reconcilerFactory.create(
                dbReader = dbReconciler!!,
                kafkaReader = reconcilerReader,
                writer = reconcilerWriter,
                keyClass = String::class.java,
                valueClass = HostedIdentityEntry::class.java,
                reconciliationIntervalMs = intervalMillis,
                forceInitialReconciliation = true,
            ).also { it.start() }
        } else {
            logger.info("Updating Hosted Identity ${Reconciler::class.java.name}")
            reconciler!!.updateInterval(intervalMillis)
        }
    }

    override fun stop() {
        dbReconciler?.stop()
        dbReconciler = null
        reconciler?.stop()
        reconciler = null
    }

    private fun getAllHostedIdentities(context: ReconciliationContext): Stream<VersionedRecord<String, HostedIdentityEntry>> {
        val entityManager = context.getOrCreateEntityManager()
        val identityQuery = entityManager.criteriaBuilder.createQuery(HostedIdentityEntity::class.java)
        val identityRoot = identityQuery.from(HostedIdentityEntity::class.java)
        identityQuery.select(identityRoot)
        val hostedIdentityEntities = entityManager.createQuery(identityQuery).resultList.stream().map {
            it.toHostedIdentityEntry(entityManager)
        }
        return hostedIdentityEntities.toVersionedRecords()
    }

    private fun HostedIdentityEntity.toHostedIdentityEntry(em: EntityManager): HostedIdentityEntry {
        val holdingId = ShortHash.of(holdingIdentityShortHash)
        val keysQuery = em.criteriaBuilder.createQuery(HostedIdentitySessionKeyInfoEntity::class.java)
        val keysRoot = keysQuery.from(HostedIdentitySessionKeyInfoEntity::class.java)
        keysQuery
            .select(keysRoot)
            .where(
                em.criteriaBuilder.equal(
                    keysRoot.get<String>(HostedIdentitySessionKeyInfoEntity::holdingIdentityShortHash.name),
                    holdingIdentityShortHash
                )
            )
        val (preferredKey, alternateKeys) = em.createQuery(keysQuery).resultList.let { allKeys ->
            val preferred = allKeys.firstOrNull { it.sessionKeyId == preferredSessionKeyAndCertificate }
                ?: throw CordaRuntimeException("Failed to retrieve preferred session key for '$holdingIdentityShortHash'.")
            preferred.toSessionKeyAndCertificate() to (allKeys - preferred).map { it.toSessionKeyAndCertificate() }
        }

        val (tlsKeyTenantId, tlsCertificateHoldingId) = when (useClusterLevelTlsCertificateAndKey) {
            true -> P2P to null
            false -> holdingIdentityShortHash to holdingId
        }
        val tlsCertificates = getCertificates(tlsCertificateHoldingId, tlsCertificateChainAlias, em)
        return HostedIdentityEntry.newBuilder()
            .setHoldingIdentity(getHoldingIdentity(holdingId).toAvro())
            .setTlsCertificates(tlsCertificates)
            .setTlsTenantId(tlsKeyTenantId)
            .setPreferredSessionKeyAndCert(preferredKey)
            .setAlternativeSessionKeysAndCerts(alternateKeys)
            .setVersion(version)
            .build()
    }

    private fun Stream<HostedIdentityEntry>.toVersionedRecords(): Stream<VersionedRecord<String, HostedIdentityEntry>> {
        return map {
            object : VersionedRecord<String, HostedIdentityEntry> {
                override val version = it.version
                override val isDeleted = false
                override val key = it.holdingIdentity.toCorda().shortHash.value
                override val value = it
            }
        }
    }

    private val entitiesSet by lazy {
        jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
            ?: throw CordaRuntimeException(
                "persistenceUnitName '${CordaDb.Vault.persistenceUnitName}' is not registered."
            )
    }

    private fun HostedIdentitySessionKeyInfoEntity.toSessionKeyAndCertificate(): HostedIdentitySessionKeyAndCert {
        val holdingId = ShortHash.of(holdingIdentityShortHash)

        val vnodeEntityManager = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingId)?.let {
            VirtualNodeReconciliationContext(dbConnectionManager, entitiesSet, it)
        }?.getOrCreateEntityManager() ?:
        throw CertificatesResourceNotFoundException("Virtual Node with '$holdingIdentityShortHash' not found.")

        val sessionCertificate = sessionCertificateAlias?.let { alias ->
            vnodeEntityManager.find(Certificate::class.java, alias)?.rawCertificate?.toPemCertificateChain()
                ?: throw CertificatesResourceNotFoundException("Certificate with '$alias' not found.")
        }
        return HostedIdentitySessionKeyAndCert.newBuilder()
            .setSessionPublicKey(
                getSessionKey(
                    holdingId.value,
                    ShortHash.of(sessionKeyId),
                )
            )
            .setSessionCertificates(sessionCertificate)
            .build()
    }

    private fun getCertificates(
        certificateHoldingId: ShortHash?,
        certificateChainAlias: String,
        clusterLevelEntityManager: EntityManager
    ): List<String> {
        val (entityManager, type) = if (certificateHoldingId != null) {
            val entityManager = virtualNodeInfoReadService.getByHoldingIdentityShortHash(certificateHoldingId)?.let {
                VirtualNodeReconciliationContext(dbConnectionManager, entitiesSet, it)
            }?.getOrCreateEntityManager()
                ?: throw CertificatesResourceNotFoundException("Virtual Node with '$certificateHoldingId' not found.")
            entityManager to Certificate::class.java
        } else {
            clusterLevelEntityManager to ClusterCertificate::class.java
        }
        return entityManager.find(type, certificateChainAlias)?.rawCertificate?.toPemCertificateChain()
                ?: throw CertificatesResourceNotFoundException("Certificate with '$certificateChainAlias' not found.")
    }

    private fun getSessionKey(
        tenantId: String,
        sessionKeyId: ShortHash,
    ): String {
        val cachedCertificate = cachedKeys.getIfPresent(KeyLookup(tenantId, sessionKeyId))
        return if (cachedCertificate != null) {
            cachedCertificate
        } else {
            val key = reconcilerCryptoOpsClient.lookupKeysByIds(tenantId, listOf(sessionKeyId)).firstOrNull()?.toPem()
                ?: throw CertificatesResourceNotFoundException("Can not find session key for $tenantId")
            cachedKeys.put(KeyLookup(tenantId, sessionKeyId), key)
            key
        }
    }

    private fun CryptoSigningKey.toPem(): String {
        return keyEncodingService.encodeAsString(
            keyEncodingService.decodePublicKey(
                this.publicKey.array()
            )
        )
    }

    private fun getHoldingIdentity(holdingIdentityShortHash: ShortHash): HoldingIdentity {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
            ?: throw CordaRuntimeException("No virtual node with ID $holdingIdentityShortHash")
    }
}
