package net.corda.ledger.utxo.token.cache.impl.services

import java.util.stream.Stream
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.schema.configuration.ConfigKeys
import net.corda.ledger.utxo.token.cache.impl.MINIMUM_SMART_CONFIG
import net.corda.ledger.utxo.token.cache.services.TokenCacheComponent
import net.corda.ledger.utxo.token.cache.services.TokenCacheSubscriptionHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class TokenCacheComponentTest {

    companion object {
        @JvmStatic
        fun dependants(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<TokenCacheSubscriptionHandler>()),
            )
        }
    }

    private val tokenCacheSubscriptionHandler = mock<TokenCacheSubscriptionHandler>()

    private val exampleConfig = mapOf(
        ConfigKeys.BOOT_CONFIG to MINIMUM_SMART_CONFIG,
        ConfigKeys.MESSAGING_CONFIG to MINIMUM_SMART_CONFIG
    )

    @Test
    fun `start event starts the subscription handler`() {
        val context = getTokenCacheComponentTestContext()
        context.run {
            testClass.start()

            verify(tokenCacheSubscriptionHandler).start()
        }
    }

    @Test
    fun `configuration service event registration once all dependent components are up`() {
        val context = getTokenCacheComponentTestContext()
        val flowServiceCoordinator = context.getCoordinatorFor<TokenCacheComponent>()
        context.run {
            testClass.start()

            verify(this.configReadService, times(0)).registerComponentForUpdates(any(), any())

            bringDependenciesUp()

            verify(this.configReadService).registerComponentForUpdates(
                eq(flowServiceCoordinator),
                eq(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG))
            )
        }
    }

    @Test
    fun `on configuration event mark service up`() {
        val context = getTokenCacheComponentTestContext()

        context.run {
            testClass.start()
            bringDependenciesUp()

            sendConfigUpdate<TokenCacheComponent>(exampleConfig)

            verifyIsUp<TokenCacheComponent>()
        }
    }

    @Test
    fun `on configuration event configures services`() {
        val context = getTokenCacheComponentTestContext()

        context.run {
            testClass.start()
            bringDependenciesUp()

            sendConfigUpdate<TokenCacheComponent>(exampleConfig)

            verify(tokenCacheSubscriptionHandler).onConfigChange(any())
        }
    }

    @Test
    fun `on all dependents up flow service should not be up`() {
        val context = getTokenCacheComponentTestContext()

        context.run {
            testClass.start()
            bringDependenciesUp()

            verifyIsDown<TokenCacheComponent>()
        }
    }

    @ParameterizedTest(name = "on component {0} going down the flow service should go down")
    @MethodSource("dependants")
    fun `on any dependent going down the flow service should go down`(name: LifecycleCoordinatorName) {
        val context = getTokenCacheComponentTestContext()

        context.run {
            testClass.start()

            bringDependenciesUp()
            sendConfigUpdate<TokenCacheComponent>(exampleConfig)
            verifyIsUp<TokenCacheComponent>()

            bringDependencyDown(name)

            verifyIsDown<TokenCacheComponent>()
        }
    }

    @ParameterizedTest(name = "on component {0} going down the token cache component should go to error")
    @MethodSource("dependants")
    fun `on any dependent going to error the token cache service should go down`(name: LifecycleCoordinatorName) {
        val context = getTokenCacheComponentTestContext()

        context.run {
            testClass.start()

            bringDependenciesUp()
            sendConfigUpdate<TokenCacheComponent>(exampleConfig)
            verifyIsUp<TokenCacheComponent>()

            setDependencyToError(name)

            verifyIsDown<TokenCacheComponent>()
        }
    }

    private fun getTokenCacheComponentTestContext(): LifecycleTest<TokenCacheComponent> {
        return LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<TokenCacheSubscriptionHandler>()

            TokenCacheComponent(
                coordinatorFactory,
                configReadService,
                tokenCacheSubscriptionHandler
            )
        }
    }
}
