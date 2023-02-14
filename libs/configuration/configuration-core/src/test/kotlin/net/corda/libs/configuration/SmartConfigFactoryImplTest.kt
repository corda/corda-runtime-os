package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.libs.configuration.secret.SecretsLookupService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SmartConfigFactoryImplTest {
    private val secretsLookupService = mock<SecretsLookupService>()
    private val secretsCreateService = mock<SecretsCreateService>() {
        on { createValue(any(), any()) }.doReturn(mock())
    }
    private val config = ConfigFactory.parseString("foo=bar")

    @Test
    fun `when create inject factory`() {
        val factory = SmartConfigFactoryImpl(secretsLookupService, secretsCreateService)

        val sc = factory.create(config)

        assertThat(sc.factory).isEqualTo(factory)
    }

    @Test
    fun `when secretsLookupService expose`() {
        val factory = SmartConfigFactoryImpl(secretsLookupService, secretsCreateService)
        assertThat(factory.secretsLookupService).isEqualTo(secretsLookupService)
    }

    @Test
    fun `when create wrap config`() {
        val factory = SmartConfigFactoryImpl(secretsLookupService, secretsCreateService)

        val sc = factory.create(config)

        assertThat(sc).isEqualTo(config)
    }

    @Test
    fun `when makeSecret use SecretsCreateService`() {
        val factory = SmartConfigFactoryImpl(secretsLookupService, secretsCreateService)

        factory.makeSecret("hello", "test")

        verify(secretsCreateService).createValue("hello", "test")
    }
}