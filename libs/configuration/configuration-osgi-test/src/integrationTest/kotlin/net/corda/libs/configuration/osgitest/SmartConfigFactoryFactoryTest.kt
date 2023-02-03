package net.corda.libs.configuration.osgitest

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.SecretsService
import net.corda.libs.configuration.secret.SecretsServiceFactory
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.service.component.annotations.Component
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

// dummy SecretsServiceFactory to test the discovery of multiple factories.
@Component(service = [SecretsServiceFactory::class])
class MI5Factory : SecretsServiceFactory {
    override val type: String
        get() = "MI5"

    override fun create(secretsServiceConfig: Config): SecretsService {
        return MI5(secretsServiceConfig.getString("spy"))
    }

    class MI5(private val spy: String): SecretsService {
        override fun createValue(plainText: String, @Suppress("UNUSED_PARAMETER") key: String): Config {
            return ConfigFactory.parseMap(
                mapOf(
                    "configSecret.code" to spy,
                    "configSecret.name" to plainText
                )
            )
        }

        override fun getValue(secretConfig: Config): String {
            return secretConfig.getString("configSecret.name")
        }
    }
}

@ExtendWith(ServiceExtension::class)
class SmartConfigFactoryAndSecretsServiceFactoryResolverTest {
    companion object {
        private const val INJECT_TIMEOUT = 10000L

        @InjectService(timeout = INJECT_TIMEOUT)
        lateinit var secretsServiceFactoryResolver: SecretsServiceFactoryResolver
    }

    @Test
    fun `OsgiSecretsServiceFactoryResolver should resolve all implementations`() {
        assertThat(secretsServiceFactoryResolver.findAll().map { it.javaClass.name })
            .containsExactlyInAnyOrder(
                MI5Factory::class.java.name,
                EncryptionSecretsServiceFactory::class.java.name)
    }

    @Test
    fun `when config only supplies salt and passphrase, use EncryptionSecretsService`() {
        val secretsConfig = mapOf(
            EncryptionSecretsServiceFactory.SECRET_SALT_KEY to "salt",
            EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY to "pass"
        )
        val configFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseMap(secretsConfig), secretsServiceFactoryResolver.findAll()
        )
        val secretConfig = configFactory.makeSecret("hello", "test")

        // NOTE: a bit a "hacky" way of validating, but if we return a config object here that contains the correct
        //   json structure, then we can be fairly confident the correct factory was used
        val typeSafeConfig = ConfigFactory.parseString(secretConfig.root().render())
        assertThat(typeSafeConfig.hasPath("configSecret.encryptedSecret")).isTrue

        // do the reverse
        val smartConfig = configFactory.create(typeSafeConfig.atKey("foo"))
        assertThat(smartConfig.getString("foo")).isEqualTo("hello")
    }

    @Test
    fun `when config supplies type MI5, use dummy secrets service`() {
        val secretsConfig = mapOf(SmartConfigFactory.SECRET_SERVICE_TYPE to "MI5", "spy" to "007")
        val configFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseMap(secretsConfig), secretsServiceFactoryResolver.findAll()
        )
        val secretConfig = configFactory.makeSecret("bond", "james")

        val typeSafeConfig = ConfigFactory.parseString(secretConfig.root().render())
        assertThat(typeSafeConfig.getString("configSecret.code")).isEqualTo("007")
        assertThat(typeSafeConfig.getString("configSecret.name")).isEqualTo("bond")

        // do the reverse
        val smartConfig = configFactory.create(typeSafeConfig.atKey("foo"))
        assertThat(smartConfig.getString("foo")).isEqualTo("bond")
    }
}