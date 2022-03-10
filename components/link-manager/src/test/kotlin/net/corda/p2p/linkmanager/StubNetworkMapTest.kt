package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class StubNetworkMapTest {
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val instanceId = 321
    private val configuration = mock<SmartConfig>()
    private val identities = mockConstruction(StubIdentitiesNetworkMap::class.java)
    private val groups = mockConstruction(StubGroupsNetworkMap::class.java)
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
    }

    private val networkMap = StubNetworkMap(
        lifecycleCoordinatorFactory, subscriptionFactory, instanceId, configuration
    )

    @AfterEach
    fun cleanUp() {
        identities.close()
        groups.close()
        dominoTile.close()
    }

    @Test
    fun `getMemberInfo throws exception when not running`() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(false)

        assertThrows<IllegalStateException> {
            networkMap.getMemberInfo(LinkManagerNetworkMap.HoldingIdentity("name", "group"))
        }
    }

    @Test
    fun `getMemberInfo return identity member info`() {
        val identity = LinkManagerNetworkMap.HoldingIdentity("name", "group")
        val info = LinkManagerNetworkMap.MemberInfo(
            identity,
            mock(),
            KeyAlgorithm.ECDSA,
            LinkManagerNetworkMap.EndPoint("point")
        )
        whenever(identities.constructed().first().getMemberInfo(identity)).doReturn(info)

        assertThat(networkMap.getMemberInfo(identity)).isSameAs(info)
    }

    @Test
    fun `getMemberInfo by hash throws exception when not running`() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(false)

        assertThrows<IllegalStateException> {
            networkMap.getMemberInfo(ByteArray(0), "group")
        }
    }

    @Test
    fun `getMemberInfo by hash return identity member info`() {
        val hash = "hash".toByteArray()
        val identity = LinkManagerNetworkMap.HoldingIdentity("name", "group")
        val info = LinkManagerNetworkMap.MemberInfo(
            identity,
            mock(),
            KeyAlgorithm.ECDSA,
            LinkManagerNetworkMap.EndPoint("point")
        )
        whenever(identities.constructed().first().getMemberInfo(hash, "group")).doReturn(info)

        assertThat(networkMap.getMemberInfo(hash, "group")).isSameAs(info)
    }

    @Test
    fun `getNetworkType throws exception when not running`() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(false)

        assertThrows<IllegalStateException> {
            networkMap.getNetworkType("group")
        }
    }

    @Test
    fun `getNetworkType returns CORDA-5 when needed`() {
        whenever(groups.constructed().first().getGroupInfo("group")).doReturn(
            NetworkMapListener.GroupInfo(
                "group",
                NetworkType.CORDA_5,
                emptySet(),
                emptyList()
            )
        )

        assertThat(networkMap.getNetworkType("group")).isEqualTo(LinkManagerNetworkMap.NetworkType.CORDA_5)
    }

    @Test
    fun `getNetworkType returns CORDA-4 when needed`() {
        whenever(groups.constructed().first().getGroupInfo("group")).doReturn(
            NetworkMapListener.GroupInfo(
                "group",
                NetworkType.CORDA_4,
                emptySet(),
                emptyList()
            )
        )

        assertThat(networkMap.getNetworkType("group")).isEqualTo(LinkManagerNetworkMap.NetworkType.CORDA_4)
    }

    @Test
    fun `getNetworkType returns null when unknown`() {
        whenever(groups.constructed().first().getGroupInfo("group")).doReturn(null)

        assertThat(networkMap.getNetworkType("group")).isNull()
    }

    @Test
    fun `getProtocolModes throws exception when not running`() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(false)

        assertThrows<IllegalStateException> {
            networkMap.getProtocolModes("group")
        }
    }

    @Test
    fun `getProtocolModes returns correct modes`() {
        whenever(groups.constructed().first().getGroupInfo("group")).doReturn(
            NetworkMapListener.GroupInfo(
                "group",
                NetworkType.CORDA_5,
                setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
                emptyList()
            )
        )

        assertThat(networkMap.getProtocolModes("group")).containsExactlyInAnyOrder(ProtocolMode.AUTHENTICATED_ENCRYPTION)
    }

    @Test
    fun `getProtocolModes returns null when not found`() {
        whenever(groups.constructed().first().getGroupInfo("group")).doReturn(null)

        assertThat(networkMap.getProtocolModes("group")).isNull()
    }

    @Test
    fun `registerListener register to group listener`() {
        val listener = mock<NetworkMapListener>()

        networkMap.registerListener(listener)

        verify(groups.constructed().first()).registerListener(listener)
    }
}
