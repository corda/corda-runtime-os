package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.crypto.core.SecureHashImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.repositories.UtxoTokenRepository
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetricsImpl
import net.corda.ledger.utxo.token.cache.services.internal.AvailableTokenServiceImpl
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class AvailableTokenServiceImplTest {
    private val totalBalance = BigDecimal(10)
    private val uuid = UUID.randomUUID()

    private val virtualNode = mock<VirtualNodeInfo>().apply {
        whenever(vaultDmlConnectionId).thenReturn(uuid)
    }
    val entityManagerFactory = mock<EntityManagerFactory>().apply {
        whenever(createEntityManager()).thenReturn(mock<EntityManager>())
    }
    private val virtualNodeInfoService = mock<VirtualNodeInfoReadService>().apply {
        whenever(getByHoldingIdentityShortHash(any())).thenReturn(virtualNode)
    }
    private val dbConnectionManager = mock<DbConnectionManager>().apply {
        whenever(getOrCreateEntityManagerFactory(eq(uuid), any(), any())).thenReturn(entityManagerFactory)
    }
    private val utxoTokenRepository = mock<UtxoTokenRepository>().apply {
        whenever(queryBalance(any(), any(), isNull(), isNull())).thenReturn(totalBalance)
    }

    private val poolKey = mock<TokenPoolKey>().apply {
        whenever(shortHolderId).thenReturn(SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "random".toByteArray()).toHexString())
    }

    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry>().apply {
        whenever(get(any())).thenReturn(JpaEntitiesSet.create("empty", emptySet()))
    }

    private val tokenSelectionMetrics = TokenSelectionMetricsImpl()
    private val availableTokenService = AvailableTokenServiceImpl(
        virtualNodeInfoService,
        dbConnectionManager,
        jpaEntitiesRegistry,
        utxoTokenRepository,
        tokenSelectionMetrics
    )

    /**
     * Matching rule:
     * null value in the query criteria matches anything
     */
    @Test
    fun `Total balance must match available balance when there is not claimed tokens`() {
        val result = availableTokenService.queryBalance(poolKey, null, null, emptySet())

        assertThat(result.totalBalance).isEqualTo(totalBalance)
        assertThat(result.availableBalance).isEqualTo(totalBalance)
    }

    @Test
    fun `Available balance is calculated correctly based on the claimed tokens`() {
        val claimedTokens = setOf(createToken(1), createToken(2), createToken(3))
        val result = availableTokenService.queryBalance(poolKey, null, null, claimedTokens)
        val expectResult = totalBalance - claimedTokens.sumOf { it.amount }

        assertThat(result.totalBalance).isEqualTo(totalBalance)
        assertThat(result.availableBalance).isEqualTo(expectResult)
    }

    private fun createToken(amount: Long): CachedToken {
        return mock<CachedToken>().apply {
            whenever(this.amount).thenReturn(BigDecimal(amount))
        }
    }
}
