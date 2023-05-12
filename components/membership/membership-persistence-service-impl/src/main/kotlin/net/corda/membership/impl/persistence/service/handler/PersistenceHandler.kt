package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
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
    private val virtualNodeInfoReadService get() = persistenceHandlerServices.virtualNodeInfoReadService
    val clock get() = persistenceHandlerServices.clock
    val cordaAvroSerializationFactory get() = persistenceHandlerServices.cordaAvroSerializationFactory
    val memberInfoFactory get() = persistenceHandlerServices.memberInfoFactory
    val keyEncodingService get() = persistenceHandlerServices.keyEncodingService
    val platformInfoProvider get() = persistenceHandlerServices.platformInfoProvider
    val allowedCertificatesReaderWriterService get() = persistenceHandlerServices.allowedCertificatesReaderWriterService

    fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R {
        val start = myTestClock.instant()
        val id = UUID.randomUUID()
        logger.info(
            "DB investigation " +
                    "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                    "- 1 " +
                    "- $id " +
                    "- ${myTestClock.instant().nano} "
        )
        val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
            ?: throw MembershipPersistenceException(
                "Virtual node info can't be retrieved for " +
                        "holding identity ID $holdingIdentityShortHash"
            )
        logger.info(
            "DB investigation " +
                    "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                    "- 2 " +
                    "- $id " +
                    "- ${myTestClock.instant().nano} "
        )
        val factory = getEntityManagerFactory(virtualNodeInfo)
        logger.info(
            "DB investigation " +
                    "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                    "- 3 " +
                    "- $id " +
                    "- ${myTestClock.instant().nano} "
        )
        return try {
            factory.transaction(block).also {
                logger.info(
                "DB investigation " +
                        "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                        "- 4 " +
                        "- $id " +
                        "- ${myTestClock.instant().nano} "
            )
            }
        } finally {
            factory.close().also{
                logger.info(
                    "DB investigation " +
                            "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                            "- 5 " +
                            "- $id " +
                            "- ${myTestClock.instant().nano} "
                )
            }
        }.also {
            logger.info(
                "DB investigation " +
                        "- fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R " +
                        "- total " +
                        "- $id " +
                        "- ${myTestClock.instant().minusNanos(start.nano.toLong()).nano}"
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
            logger.info(
                "DB investigation " +
                        "- fun getEntityManagerFactory(info: VirtualNodeInfo): EntityManagerFactory " +
                        "- total " +
                        "- $id " +
                        "- ${myTestClock.instant().minusNanos(start.nano.toLong()).nano}"
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
)
