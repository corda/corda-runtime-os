package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class SmartConfigObjectTest {
    val configString = """
        root {
            foo: bar,
            fred {
                isSmartConfigSecret: true,
                token: secure-fred
            },
        }
        """.trimIndent()
    val config = ConfigFactory.parseString(configString)
    val otherConfig = ConfigFactory.parseString(
        """
        jon {
            isSmartConfigSecret: true,
            token: secure-jon
        },
        """.trimIndent()
    )

    val secretsLookupService = mock<SecretsLookupService>() {
        on { getValue(config.getValue("root.fred"))} doReturn "secret"
        on { getValue(otherConfig.getValue("jon"))} doReturn "other-secret"
    }
    val configObject: SmartConfigObject =
        SmartConfigObjectImpl(config.getObject("root"), secretsLookupService)

    @Test
    fun `toSafeConfig never reveals secrets`() {
        assertThat(configObject.toSafeConfig().toConfig().getString("fred"))
            .isEqualTo("*****")
    }

    @Test
    fun `render never reveals secrets`() {
        println(configObject.render())
        verifyNoInteractions(secretsLookupService)
    }

    @Test
    fun `withOnlyKey still works`() {
        val conf = configObject.withOnlyKey("fred")
        assertThat(conf.toConfig().hasPath("foo")).isFalse
        assertThat(conf.toConfig().getString("fred")).isEqualTo("secret")
    }

    @Test
    fun `withoutKey still works`() {
        val conf = configObject.withoutKey("foo")
        assertThat(conf.toConfig().hasPath("foo")).isFalse
        assertThat(conf.toConfig().getString("fred")).isEqualTo("secret")
    }

    @Test
    fun `withValue still works`() {
        val conf = configObject.withValue("hello", otherConfig.getValue("jon"))
        assertThat(conf.toConfig().getString("hello")).isEqualTo("other-secret")
    }

    @Test
    fun `atPath still works`() {
        val conf = configObject.atPath("hello.world")
        assertThat(conf.getConfig("hello").getString("world.foo")).isEqualTo("bar")
        assertThat(conf.getConfig("hello").getString("world.fred")).isEqualTo("secret")
    }

    @Test
    fun `atKey still works`() {
        val conf = configObject.atKey("hello")
        assertThat(conf.getConfig("hello").getString("foo")).isEqualTo("bar")
        assertThat(conf.getConfig("hello").getString("fred")).isEqualTo("secret")
    }

    @Test
    fun `fallback still works`() {
        val fb = configObject.withFallback(otherConfig)
        assertThat(fb.toConfig().getString("jon")).isEqualTo("other-secret")
    }

    @Test
    fun `origin still works`() {
        assertThat(configObject.origin()).isEqualTo(config.origin())
    }

    @Test
    fun `isEmpty still works`() {
        assertThat(configObject.isEmpty()).isFalse
    }
}