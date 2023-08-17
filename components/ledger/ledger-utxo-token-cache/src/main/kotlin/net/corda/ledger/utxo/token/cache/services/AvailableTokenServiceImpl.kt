package net.corda.ledger.utxo.token.cache.services

import net.corda.crypto.core.ShortHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.repositories.UtxoTokenRepository
import net.corda.ledger.utxo.token.cache.entities.TokenBalance
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.VirtualNodeInfo

@Component(service = [AvailableTokenService::class])
class AvailableTokenServiceImpl @Activate constructor(
    @Reference
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    @Reference
    private val dbConnectionManager: DbConnectionManager,
    @Reference
    private val utxoTokenRepository: UtxoTokenRepository
) : AvailableTokenService, SingletonSerializeAsToken {
    override fun findAvailTokens(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?): AvailTokenQueryResult {
        val virtualNode = getVirtualNodeInfo(poolKey)

        val entityManagerFactory = getOrCreateEntityManagerFactory(virtualNode)

        return utxoTokenRepository.findTokens(entityManagerFactory.createEntityManager(), poolKey, ownerHash, tagRegex)
    }

    override fun queryBalance(
        poolKey: TokenPoolKey,
        ownerHash: String?,
        tagRegex: String?,
        claimedTokens: Collection<CachedToken>
    ): TokenBalance {
        val virtualNode = getVirtualNodeInfo(poolKey)
        val entityManagerFactory = getOrCreateEntityManagerFactory(virtualNode)

        val totalBalance = utxoTokenRepository.queryBalance(entityManagerFactory.createEntityManager(), poolKey, ownerHash, tagRegex)
        val claimedBalance = claimedTokens.sumOf { it.amount }
        val availableBalance = totalBalance - claimedBalance

        return TokenBalance(availableBalance, totalBalance)
    }

    private fun getOrCreateEntityManagerFactory(virtualNode: VirtualNodeInfo) =
        dbConnectionManager.getOrCreateEntityManagerFactory(
            virtualNode.vaultDmlConnectionId,
            JpaEntitiesSet.create("emptySet", emptySet()) // No entity required. The mapping will be manually done
        )

    private fun getVirtualNodeInfo(poolKey: TokenPoolKey) =
        virtualNodeInfoService.getByHoldingIdentityShortHash(ShortHash.of(poolKey.shortHolderId))
            ?: throw VirtualNodeException("Could not get virtual node for ${poolKey.shortHolderId}")
}
