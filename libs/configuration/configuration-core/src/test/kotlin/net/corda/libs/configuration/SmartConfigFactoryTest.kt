package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsConfigurationException
import net.corda.libs.configuration.secret.SecretsService
import net.corda.libs.configuration.secret.SecretsServiceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SmartConfigFactoryTest {
    private val secretsServiceConfig = ConfigFactory.parseMap(mapOf(SmartConfigFactory.SECRET_SERVICE_TYPE to "duck"))
    private val mockSecretsServiceFactory1 = mock< SecretsServiceFactory> {
        on { type } doReturn ("donald")
    }
    private val mockSecretsService = mock<SecretsService>()
    private val mockSecretsServiceFactory2 = mock< SecretsServiceFactory> {
        on { type } doReturn ("duck")
        on { create(secretsServiceConfig) } doReturn (mockSecretsService)
    }

    @Test
    fun `when create, choose matching secrets provider from list`() {
        val cf = SmartConfigFactory.createWith(secretsServiceConfig, listOf(mockSecretsServiceFactory1, mockSecretsServiceFactory2))
        val config = ConfigFactory.parseMap(
            mapOf(SmartConfig.SECRET_KEY to mapOf(
                "fred" to "jon"
            )))
        val smartConfig = cf.create(config.atKey("foo"))
        smartConfig.getString("foo")

        verify(mockSecretsService).getValue(config)
    }

    @Test
    fun `when create and no matching secrets provider, throw`() {
        assertThrows<SecretsConfigurationException> {
            SmartConfigFactory.createWith(
                ConfigFactory.parseMap(mapOf(SmartConfigFactory.SECRET_SERVICE_TYPE to "micky")),
                listOf(mockSecretsServiceFactory1)
            )
        }
    }

    @Test
    fun `when createWithoutSecurityServices used masked`() {
        val cf = SmartConfigFactory.createWithoutSecurityServices()
        val config = ConfigFactory.parseMap(
            mapOf(SmartConfig.SECRET_KEY to mapOf(
                "fred" to "jon"
            )))
        val smartConfig = cf.create(config.atKey("foo"))
        assertThat(smartConfig.getString("foo")).isEqualTo(MaskedSecretsLookupService.MASK_VALUE)
    }
}