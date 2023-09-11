package net.corda.ledger.utxo.token.cache.services.internal

import net.corda.crypto.core.ShortHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.repositories.UtxoTokenRepository
import net.corda.ledger.utxo.token.cache.entities.TokenBalance
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.VirtualNodeInfo

class AvailableTokenServiceImpl(
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val utxoTokenRepository: UtxoTokenRepository,
    private val serviceConfiguration: ServiceConfiguration
) : AvailableTokenService, SingletonSerializeAsToken {
    override fun findAvailTokens(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?): AvailTokenQueryResult {
        val virtualNode = getVirtualNodeInfo(poolKey)

        val entityManagerFactory = getOrCreateEntityManagerFactory(virtualNode)

        return utxoTokenRepository.findTokens(
            entityManagerFactory.createEntityManager(),
            poolKey,
            ownerHash,
            tagRegex,
            serviceConfiguration.cachedTokenPageSize
        )
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
            jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw IllegalStateException("persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered.")
        )

    private fun getVirtualNodeInfo(poolKey: TokenPoolKey) =
        virtualNodeInfoService.getByHoldingIdentityShortHash(ShortHash.of(poolKey.shortHolderId))
            ?: throw VirtualNodeException("Could not get virtual node for ${poolKey.shortHolderId}")
}
