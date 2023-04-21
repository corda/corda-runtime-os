package net.corda.external.messaging.services

import net.corda.crypto.core.SecureHashImpl
import net.corda.external.messaging.entities.VirtualNodeRouteKey
import net.corda.external.messaging.services.impl.VirtualNodeRouteConfigInfoServiceImpl
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.entities.Route
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.external.messaging.entities.Routes
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializer
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class VirtualNodeRouteConfigInfoServiceImplTest {
    private val externalMessagingRouteConfig1 = "c1"
    private val externalMessagingRouteConfig2 = "c2"
    private val route1 = createRoute("c1", "t1")
    private val route2 = createRoute("c2", "t2")
    private val route3 = createRoute("c3", "t3")
    private val route4 = createRoute("c4", "t4")
    private val routeConfig1 = RouteConfiguration(createRoutes(route1, route2), listOf())
    private val routeConfig2 = RouteConfiguration(createRoutes(route3, route4), listOf())
    private val vNode1 = createVNode(ALICE_HOLDING_ID, externalMessagingRouteConfig1)
    private val vNode2 = createVNode(BOB_HOLDING_ID, externalMessagingRouteConfig2)
    private val vNode3 = createVNode(CHARLIE_HOLDING_ID,null)

    private val virtualNodeListenerCaptor = argumentCaptor<VirtualNodeInfoListener>()
    private val virtualNodeListenerRegistration = mock<AutoCloseable>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>().apply {
        whenever(this.registerCallback(virtualNodeListenerCaptor.capture())).thenReturn(virtualNodeListenerRegistration)
    }
    private val externalMessagingRouteConfigSerializer = mock<ExternalMessagingRouteConfigSerializer>().apply {
        whenever(this.deserialize(externalMessagingRouteConfig1)).thenReturn(routeConfig1)
        whenever(this.deserialize(externalMessagingRouteConfig2)).thenReturn(routeConfig2)
    }

    private val target = VirtualNodeRouteConfigInfoServiceImpl(
        externalMessagingRouteConfigSerializer,
        virtualNodeInfoReadService
    )

    @Test
    fun `test closing listener closed underlying listener`() {
        val registration = target.registerCallback { _ -> }
        registration.close()

        verify(virtualNodeListenerRegistration).close()
    }

    @Test
    fun `test virtual node changes are mapped to route config changes`() {
        var receivedMap = mapOf<VirtualNodeRouteKey, Route>()
        target.registerCallback { current -> receivedMap = current }

        virtualNodeListenerCaptor.firstValue.onUpdate(
            setOf(),
            mapOf(
                vNode1.holdingIdentity to vNode1,
                vNode2.holdingIdentity to vNode2,
                vNode3.holdingIdentity to vNode3,
            )
        )

        val expectedMap = mapOf(
            VirtualNodeRouteKey(ALICE_HOLDING_ID.shortHash.toString(),"c1") to  route1,
            VirtualNodeRouteKey(ALICE_HOLDING_ID.shortHash.toString(),"c2") to  route2,
            VirtualNodeRouteKey(BOB_HOLDING_ID.shortHash.toString(),"c3") to  route3,
            VirtualNodeRouteKey(BOB_HOLDING_ID.shortHash.toString(),"c4") to  route4,
        )

        assertThat(receivedMap).isEqualTo(expectedMap)
    }

    private fun createVNode(holdingId: HoldingIdentity, externalMessagingRouteConfig: String?): VirtualNodeInfo {
        return VirtualNodeInfo(
            holdingId,
            CpiIdentifier("", "", SecureHashImpl("alg", "abc".toByteArray())),
            cryptoDmlConnectionId = UUID.randomUUID(),
            vaultDmlConnectionId = UUID.randomUUID(),
            uniquenessDmlConnectionId = UUID.randomUUID(),
            timestamp = Instant.now(),
            externalMessagingRouteConfig = externalMessagingRouteConfig
        )
    }

    private fun createRoute(channelName: String, externalReceiveTopicName: String): Route {
        return Route(channelName, externalReceiveTopicName, true, InactiveResponseType.IGNORE)
    }

    private fun createRoutes(vararg routes: Route): Routes {
        return Routes(
            CpiIdentifier("", "", SecureHashImpl("alg", "abc".toByteArray())),
            listOf(*routes)
        )
    }
}
