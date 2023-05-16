package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.persistence.service.EntityManagersPool
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

internal interface PersistenceHandler<REQUEST, RESPONSE> {
    fun invoke(context: MembershipRequestContext, request: REQUEST): RESPONSE?
}

internal abstract class BasePersistenceHandler<REQUEST, RESPONSE>(
    private val persistenceHandlerServices: PersistenceHandlerServices
) : PersistenceHandler<REQUEST, RESPONSE> {
    private val myTestClock = UTCClock()

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dbConnectionManager get() = persistenceHandlerServices.dbConnectionManager
    private val jpaEntitiesRegistry get() = persistenceHandlerServices.jpaEntitiesRegistry
    val clock get() = persistenceHandlerServices.clock
    val cordaAvroSerializationFactory get() = persistenceHandlerServices.cordaAvroSerializationFactory
    val memberInfoFactory get() = persistenceHandlerServices.memberInfoFactory
    val keyEncodingService get() = persistenceHandlerServices.keyEncodingService
    val platformInfoProvider get() = persistenceHandlerServices.platformInfoProvider
    val allowedCertificatesReaderWriterService get() = persistenceHandlerServices.allowedCertificatesReaderWriterService

    fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R {
        val start = myTestClock.instant()
        var curr: Instant
        var last = start
        val id = UUID.randomUUID()
        curr = myTestClock.instant()
        logger.info(
            "DB investigation " +
                    "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                    "- 1 " +
                    "- $id " +
                    "- Current: ${curr.nano} " +
                    "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                    "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                    "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
        )
        last = curr
        return persistenceHandlerServices.entityManagersPool.getEntityManagerInfo(holdingIdentityShortHash) {
            curr = myTestClock.instant()
            logger.info(
                "DB investigation " +
                        "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                        "- 1 " +
                        "- $id " +
                        "- Current: ${curr.nano} " +
                        "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                        "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                        "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
            )
            last = curr
            it.transaction(block).also {
                curr = myTestClock.instant()
                logger.info(
                    "DB investigation " +
                            "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                            "- 2 " +
                            "- $id " +
                            "- Current: ${curr.nano} " +
                            "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                            "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                            "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
                )
                last = curr
            }
        }.also {
            curr = myTestClock.instant()
            logger.info(
                "DB investigation " +
                        "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                        "- 3 " +
                        "- $id " +
                        "- Current: ${curr.nano} " +
                        "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano }ns" +
                        "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                        "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
            )
            logger.info(
                "DB investigation " +
                        "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                        "- total " +
                        "- $id " +
                        "- Since start: ${curr.minusNanos(start.nano.toLong()).nano}ns " +
                        "- Since start: ${curr.toEpochMilli() - start.toEpochMilli()}ms " +
                        "- Since start: ${curr.epochSecond - start.epochSecond}s"
            )
        }
    }

    fun <R> transaction(block: (EntityManager) -> R): R {
        val start = net.corda.orm.utils.clock.instant()
        val id = UUID.randomUUID()
        logger.info(
            "DB investigation " +
                    "- fun <R> transaction(block: (EntityManager) -> R): R " +
                    "- 1 " +
                    "- $id " +
                    "- ${myTestClock.instant().nano} "
        )
        return dbConnectionManager.getClusterEntityManagerFactory().also {
            logger.info(
                "DB investigation " +
                        "- fun <R> transaction(block: (EntityManager) -> R): R " +
                        "- 2 " +
                        "- $id " +
                        "- ${myTestClock.instant().nano} "
            )
        }.transaction(block).also {
            logger.info(
                "DB investigation " +
                        "- fun <R> transaction(block: (EntityManager) -> R): R " +
                        "- total " +
                        "- $id " +
                        "- ${myTestClock.instant().minusNanos(start.nano.toLong()).nano}"
            )
        }
    }

    fun retrieveSignatureSpec(signatureSpec: String) = if (signatureSpec.isEmpty()) {
        CryptoSignatureSpec("", null, null)
    } else {
        CryptoSignatureSpec(signatureSpec, null, null)
    }

    private fun getEntityManagerFactory(info: VirtualNodeInfo): EntityManagerFactory {
        val start = myTestClock.instant()
        val id = UUID.randomUUID()
        return dbConnectionManager.createEntityManagerFactory(
            connectionId = info.vaultDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw java.lang.IllegalStateException(
                    "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
                )
        ).also {
            val curr = myTestClock.instant()
            logger.info(
                "DB investigation " +
                        "- fun getEntityManagerFactory(info: VirtualNodeInfo): EntityManagerFactory " +
                        "- total " +
                        "- $id " +
                        "- Since start: ${curr.minusNanos(start.nano.toLong()).nano}ns" +
                        "- Since start: ${curr.toEpochMilli() - start.toEpochMilli()}ms" +
                        "- Since start: ${curr.epochSecond - start.epochSecond}s"
            )
        }
    }
}

internal data class PersistenceHandlerServices(
    val clock: Clock,
    val dbConnectionManager: DbConnectionManager,
    val jpaEntitiesRegistry: JpaEntitiesRegistry,
    val memberInfoFactory: MemberInfoFactory,
    val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    val keyEncodingService: KeyEncodingService,
    val platformInfoProvider: PlatformInfoProvider,
    val allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService,
) {
    val entityManagersPool by lazy {
        EntityManagersPool(
            clock,
            dbConnectionManager,
            jpaEntitiesRegistry,
            virtualNodeInfoReadService,
        )
    }
}
