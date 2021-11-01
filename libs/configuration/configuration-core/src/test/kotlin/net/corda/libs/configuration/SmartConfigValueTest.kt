package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyZeroInteractions

class SmartConfigValueTest {
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
        on { getValue(
            argThat {
                this != otherConfig.getValue("jon")
            }
        )} doReturn "secret"
        on { getValue(otherConfig.getValue("jon"))} doReturn "other-secret"
    }
    val configValue: SmartConfigValue =
        SmartConfigValueImpl(config.getValue("root"), secretsLookupService)

    @Test
    fun `toSafeConfigValue never reveals secrets`() {
        assertThat(configValue.toSafeConfigValue().atPath("foo").getString("foo.fred"))
            .isEqualTo("*****")
    }

    @Test
    fun `fallback still works`() {
        val fb = configValue.withFallback(otherConfig)
        assertThat(fb!!.atPath("foo").getString("foo.jon")).isEqualTo("other-secret")
    }

    @Test
    fun `origin still works`() {
        assertThat(configValue.origin()).isEqualTo(config.origin())
    }

    @Test
    fun `unwrapped still works`() {
        val cv = SmartConfigValueImpl(config.getValue("root.foo"), secretsLookupService)
        assertThat(cv.unwrapped()).isEqualTo("bar")
    }

    @Test
    fun `unwrapped still works with secrets`() {
        val cv = SmartConfigValueImpl(config.getValue("root.fred"), secretsLookupService)
        assertThat(cv.unwrapped()).isEqualTo("secret")
    }

    @Test
    fun `render never reveals secrets`() {
        println(configValue.render())
        verifyZeroInteractions(secretsLookupService)
    }

    @Test
    fun `atPath still works`() {
        val conf = configValue.atPath("hello.world")
        assertThat(conf.getConfig("hello").getString("world.foo")).isEqualTo("bar")
        assertThat(conf.getConfig("hello").getString("world.fred")).isEqualTo("secret")
    }

    @Test
    fun `atKey still works`() {
        val conf = configValue.atKey("hello")
        assertThat(conf.getConfig("hello").getString("foo")).isEqualTo("bar")
        assertThat(conf.getConfig("hello").getString("fred")).isEqualTo("secret")
    }

    @Test
    fun `withOrigin still works`() {
        val conf = configValue.withOrigin(otherConfig.origin())
        assertThat(conf!!.origin()).isEqualTo(otherConfig.origin())
    }
}