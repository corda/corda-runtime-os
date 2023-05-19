package net.corda.membership.impl.persistence.service.handler

import io.micrometer.core.instrument.Timer
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
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

internal interface PersistenceHandler<REQUEST, RESPONSE> {
    /**
     * Persistence operation identifier for logging and metrics purposes.
     */
    val operation: Class<REQUEST>

    fun invoke(context: MembershipRequestContext, request: REQUEST): RESPONSE?
}

internal abstract class BasePersistenceHandler<REQUEST, RESPONSE>(
    private val persistenceHandlerServices: PersistenceHandlerServices
) : PersistenceHandler<REQUEST, RESPONSE> {

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

    private val transactionTimer by lazy {
        persistenceHandlerServices.transactionTimerFactory(operation.simpleName)
    }

    fun <R> transaction(holdingIdentityShortHash: ShortHash, block: (EntityManager) -> R): R {
        val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
            ?: throw MembershipPersistenceException(
                "Virtual node info can't be retrieved for " +
                        "holding identity ID $holdingIdentityShortHash"
            )
        val factory = getEntityManagerFactory(virtualNodeInfo)
        return try {
            transactionTimer.recordCallable { factory.transaction(block) }!!
        } finally {
            factory.close()
        }
    }

    fun <R> transaction(block: (EntityManager) -> R): R {
        return dbConnectionManager.getClusterEntityManagerFactory().let {
            transactionTimer.recordCallable { it.transaction(block) }!!
        }
    }

    fun retrieveSignatureSpec(signatureSpec: String) = if (signatureSpec.isEmpty()) {
        CryptoSignatureSpec("", null, null)
    } else {
        CryptoSignatureSpec(signatureSpec, null, null)
    }

    private fun getEntityManagerFactory(info: VirtualNodeInfo): EntityManagerFactory {
        return dbConnectionManager.createEntityManagerFactory(
            connectionId = info.vaultDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw java.lang.IllegalStateException(
                    "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
                )
        )
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
    val transactionTimerFactory: (String) -> Timer
)
