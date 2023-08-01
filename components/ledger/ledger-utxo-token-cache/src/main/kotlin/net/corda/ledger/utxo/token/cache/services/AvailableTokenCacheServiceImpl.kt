package net.corda.ledger.utxo.token.cache.services

import java.nio.ByteBuffer
import javax.persistence.Tuple
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory

@Component(
    service = [AvailableTokenCacheService::class],
    scope = PROTOTYPE
)
class AvailableTokenCacheServiceImpl @Activate constructor(
    @Reference
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = UtxoSignedTransactionFactory::class)
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory
) : AvailableTokenCacheService, SingletonSerializeAsToken {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun find(poolKey: TokenPoolCacheKey) {
        val virtualNode = virtualNodeInfoService.getByHoldingIdentityShortHash(ShortHash.of(poolKey.shortHolderId))
            ?: throw VirtualNodeException("Could not get virtual node for ${poolKey.shortHolderId}")


        // Follow the repository pattern
        val entityManagerFactory = dbConnectionManager.createEntityManagerFactory(
            virtualNode.vaultDmlConnectionId,
            JpaEntitiesSet.create("emptySet", emptySet()) // No entity set require
        )

        val entityManager = entityManagerFactory.createEntityManager()

        // Follow the repository pattern
        val resultList = entityManager.createNativeQuery(
            """
                SELECT
                    transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash,
                    token_symbol, token_tag, token_owner_hash, token_amount, created
                FROM {h-schema}utxo_transaction_output
                WHERE token_type = :tokenType AND
                      token_issuer_hash = :issuerHash AND
                      token_symbol = :symbol""",
            Tuple::class.java
        )
            .setParameter("tokenType", poolKey.tokenType)
            .setParameter("issuerHash", poolKey.issuerHash)
            .setParameter("symbol", poolKey.symbol)
            .resultList

        log.info("Filipe: $resultList")
    }
}
