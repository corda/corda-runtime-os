package net.corda.external.messaging.services

import com.typesafe.config.ConfigFactory
import net.corda.external.messaging.entities.VirtualNodeRouteKey
import net.corda.external.messaging.services.impl.ExternalMessagingRoutingServiceImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.entities.Route
import net.corda.messagebus.api.admin.Admin
import net.corda.messagebus.api.admin.builder.AdminBuilder
import net.corda.messagebus.api.configuration.AdminConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalMessagingRoutingServiceImplTest {
    private val route1 = Route("c1", "t1", true, InactiveResponseType.IGNORE)
    private val route2 = Route("c2", "t2", true, InactiveResponseType.IGNORE)
    private val route3 = Route("c3", "t3", true, InactiveResponseType.IGNORE)

    private val listenerCaptor = argumentCaptor<VirtualNodeRouteConfigInfoListener>()
    private val listenerRegistration = mock<AutoCloseable>()

    private val configurations = mapOf<String, SmartConfig>()
    private val messagingConfig = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())

    private val busAdmin1 = mock<Admin>()
    private val busAdmin2 = mock<Admin>()

    private val adminBuilder = mock<AdminBuilder>()
    private val virtualNodeRouteConfigInfoService = mock<VirtualNodeRouteConfigInfoService>().apply {
        whenever(this.registerCallback(listenerCaptor.capture())).thenReturn(listenerRegistration)
    }

    // Validate we are passing the configurations to the helper function
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig = { configs ->
        assertThat(configs).isEqualTo(configurations)
        messagingConfig
    }

    private val target = ExternalMessagingRoutingServiceImpl(
        adminBuilder,
        virtualNodeRouteConfigInfoService,
        toMessagingConfig
    )

    @BeforeEach
    fun setup() {
        // Bus Admin configuration and initialisation
        whenever(adminBuilder.createAdmin(AdminConfig("Messaging Routing Service"), messagingConfig))
            .thenReturn(busAdmin1, busAdmin2)

        // Setup different topic specs for the two example buses
        whenever(busAdmin1.getTopics()).thenReturn(setOf("t1"))
        whenever(busAdmin2.getTopics()).thenReturn(setOf("t1","t2","t3"))
    }

    @Test
    fun `onConfigChange multiple calls, closes previous instance of bus admin`() {
        target.onConfigChange(configurations)
        verify(busAdmin1, never()).close()
        verify(busAdmin2, never()).close()

        target.onConfigChange(configurations)
        verify(busAdmin1).close()
        verify(busAdmin2, never()).close()
    }

    @Test
    fun `onConfigChange with not virtual node data does nothing`() {
        target.onConfigChange(configurations)
    }

    @Test
    fun `virtual change with no call to onConfigChange does nothing`() {
        listenerCaptor.firstValue.onUpdate(getExampleRoutes())
    }

    @Test
    fun `get routes for configured routing service`() {
        target.onConfigChange(configurations) // using busAdmin1 so only topic t1 available
        listenerCaptor.firstValue.onUpdate(getExampleRoutes())

        assertSoftly{
            var result =target.getRoute("h1","c1")
            it.assertThat(result).isNotNull
            it.assertThat(result!!.route).isEqualTo(route1)
            it.assertThat(result.externalReceiveTopicNameExists).isEqualTo(true)

            result =target.getRoute("h1","c2")
            it.assertThat(result).isNotNull
            it.assertThat(result!!.route).isEqualTo(route2)
            it.assertThat(result.externalReceiveTopicNameExists).isEqualTo(false)

            result =target.getRoute("h2","c3")
            it.assertThat(result).isNotNull
            it.assertThat(result!!.route).isEqualTo(route3)
            it.assertThat(result.externalReceiveTopicNameExists).isEqualTo(false)
        }
    }

    @Test
    fun `get routes for configured routing service after config change`() {
        listenerCaptor.firstValue.onUpdate(getExampleRoutes())
        target.onConfigChange(configurations)
        target.onConfigChange(configurations) // using busAdmin2 so all topics available

        assertSoftly{
            var result =target.getRoute("h1","c1")
            it.assertThat(result).isNotNull
            it.assertThat(result!!.route).isEqualTo(route1)
            it.assertThat(result.externalReceiveTopicNameExists).isEqualTo(true)

            result =target.getRoute("h1","c2")
            it.assertThat(result).isNotNull
            it.assertThat(result!!.route).isEqualTo(route2)
            it.assertThat(result.externalReceiveTopicNameExists).isEqualTo(true)

            result =target.getRoute("h2","c3")
            it.assertThat(result).isNotNull
            it.assertThat(result!!.route).isEqualTo(route3)
            it.assertThat(result.externalReceiveTopicNameExists).isEqualTo(true)
        }
    }

    @Test
    fun `get routes that dont exist returns null`() {
        target.onConfigChange(configurations)
        listenerCaptor.firstValue.onUpdate(getExampleRoutes())

        assertSoftly{
            var result =target.getRoute("h1","cX")
            assertThat(result).isNull()

            result =target.getRoute("hX","c1")
            assertThat(result).isNull()
        }
    }

    private fun getExampleRoutes(): Map<VirtualNodeRouteKey, Route> {
        return mapOf(
            VirtualNodeRouteKey("h1", "c1") to route1,
            VirtualNodeRouteKey("h1", "c2") to route2,
            VirtualNodeRouteKey("h2", "c3") to route3,
        )
    }
}
