package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Duration
import java.util.concurrent.TimeUnit

class SmartConfigTest {
    val sub = "\${fred}"
    val configString = """
        foo: bar,
        fred {
            isSmartConfigSecret: true,
            token: secure-fred
        },
        bob: $sub,
        test_long: ${Double.MAX_VALUE},
        test_number: 10,
        test_boolean: true,
        test_donuts: DONUTS,
        test_double: 1.33,
        test_duration: 10ms,
        test_durations: [10ms,15ms],
        test_bytes: 128kB,
        test_object {
            a: b,
            c: d,
        },
        """.trimIndent()
    val config = ConfigFactory.parseString(configString).resolve()

    val fallbackConfig = ConfigFactory.parseString(
        """
        jon {
            isSmartConfigSecret: true,
            token: secure-jon
        },
        """.trimIndent()
    )

    val secretsLookupService = mock<SecretsLookupService>() {
        on { getValue(config.getValue("fred"))} doReturn "secret"
        on { getValue(fallbackConfig.getValue("jon"))} doReturn "fallback-secret"
    }
    val smartConfig : SmartConfig = SmartConfigImpl(config, secretsLookupService)

    @Test
    fun `isSecret true when property set`() {
        assertThat(smartConfig.isSecret("fred")).isTrue
    }

    @Test
    fun `isSecret false when property not set`() {
        assertThat(smartConfig.isSecret("foo")).isFalse
    }

    @Test
    fun `getString returns when not secret`() {
        assertThat(smartConfig.getString("foo")).isEqualTo("bar")
    }

    @Test
    fun `getString delegates to secrets service when secret`() {
        smartConfig.getString("fred")
        verify(secretsLookupService).getValue(config.getValue("fred"))
        assertThat(smartConfig.getString("fred")).isEqualTo("secret")
    }

    @Test
    fun `typesafe substitution works`() {
        assertThat(smartConfig.getString("bob")).isEqualTo("secret")
        assertThat(smartConfig.isSecret("bob")).isTrue
    }

    @Test
    fun `toString never reveals secrets`() {
        println(smartConfig)
        verifyNoInteractions(secretsLookupService)
    }

    @Test
    fun `render never reveals secrets`() {
        println(smartConfig.root().render())
        verifyNoInteractions(secretsLookupService)
    }

    @Test
    fun `toSafeConfig never reveals secrets`() {
        assertThat(smartConfig.toSafeConfig().getString("fred"))
            .isEqualTo("*****")
    }

    @Test
    fun `fallback still works`() {
        val fb = smartConfig.withFallback(fallbackConfig)
        assertThat(fb.getString("jon")).isEqualTo("fallback-secret")
    }

    @Test
    fun `origin still works`() {
        assertThat(smartConfig.origin()).isEqualTo(config.origin())
    }

    @Test
    fun `resolve still works`() {
        val unresolvedConfig = SmartConfigImpl(ConfigFactory.parseString(configString), secretsLookupService)
        val resolvedConfig = unresolvedConfig.resolve()
        assertThat(resolvedConfig.isResolved).isTrue
        assertThat(resolvedConfig.getString("bob")).isEqualTo("secret")
        assertThat(resolvedConfig.isSecret("bob")).isTrue
    }

    @Test
    fun `resolveWith still works`() {
        val sub = "\${fred}"
        val config = ConfigFactory.parseString("""
        bob: $sub
        """.trimIndent())
        val resolveWithConf = ConfigFactory.parseString(
            """
        fred {
            isSmartConfigSecret: true,
            token: secure-fred
        },
        """.trimIndent()
        )

        val smartConf = SmartConfigImpl(config, secretsLookupService).resolveWith(resolveWithConf)
        assertThat(smartConf.getString("bob")).isEqualTo("secret")
        assertThat(smartConf.isSecret("bob")).isTrue
    }

    @Test
    fun `checkValid still works`() {
        assertDoesNotThrow { smartConfig.checkValid(config) }
    }

    @Test
    fun `hasPath still works`() {
        assertThat(smartConfig.hasPath("fred")).isTrue
    }

    @Test
    fun `isEmpty still works`() {
        assertThat(smartConfig.isEmpty).isFalse
    }

    @Test
    fun `entrySet still works`() {
        assertThat(smartConfig.entrySet()).containsAll(
            mutableMapOf(
                "foo" to smartConfig.getValue("foo")
            ).entries.toMutableSet()
        )
    }

    @Test
    fun `getIsNull still works`() {
        assertThat(smartConfig.getIsNull("foo")).isFalse
    }

    @Test
    fun `getBoolean still works`() {
        assertThat(smartConfig.getBoolean("test_boolean")).isTrue
    }

    @Test
    fun `getNumber still works`() {
        assertThat(smartConfig.getNumber("test_number")).isEqualTo(10)
    }

    @Test
    fun `getInt still works`() {
        assertThat(smartConfig.getInt("test_number")).isEqualTo(10)
    }

    @Test
    fun `getLong still works`() {
        assertThat(smartConfig.getLong("test_long")).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `getDouble still works`() {
        assertThat(smartConfig.getDouble("test_double")).isEqualTo(1.33)
    }

    @Test
    fun `getEnum still works`() {
        assertThat(smartConfig.getEnum(Snack::class.java, "test_donuts")).isEqualTo(Snack.DONUTS)
    }

    @Test
    fun `getObject still works`() {
        assertThat(smartConfig.getObject("test_object").keys).containsAll(listOf("a","c"))
    }

    @Test
    fun `getConfig still works`() {
        assertThat(smartConfig.getConfig("test_object").getString("a")).isEqualTo("b")
    }

    @Test
    fun `getAnyRef still works`() {
        assertThat(smartConfig.getAnyRef("foo")).isEqualTo("bar")
    }

    @Test
    fun `getAnyRef still works with secrets`() {
        assertThat(smartConfig.getAnyRef("fred")).isEqualTo("secret")
    }

    @Test
    fun `getValue still works`() {
        assertThat(smartConfig.getValue("foo")).isEqualTo(config.getValue("foo"))
    }

    @Test
    fun `getBytes still works`() {
        assertThat(smartConfig.getBytes("test_bytes")).isEqualTo(128_000)
    }

    @Test
    fun `getMemorySize still works`() {
        assertThat(smartConfig.getMemorySize("test_bytes").toBytes()).isEqualTo(128_000)
    }

    @Test
    fun `getMilliseconds deprecated`() {
        assertThrows<UnsupportedOperationException> {
            @Suppress("DEPRECATION")
            smartConfig.getMilliseconds("test")
        }
    }

    @Test
    fun `getNanoseconds deprecated`() {
        assertThrows<UnsupportedOperationException> {
            @Suppress("DEPRECATION")
            smartConfig.getNanoseconds("test")
        }
    }

    @Test
    fun `getDuration still works`() {
        assertThat(smartConfig.getDuration("test_duration", TimeUnit.MILLISECONDS)).isEqualTo(10)
    }

    @Test
    fun `getDuration in units still works`() {
        assertThat(smartConfig.getDuration("test_duration")).isEqualTo(Duration.ofMillis(10))
    }

    @Test
    fun `getDurationList still works`() {
        assertThat(smartConfig.getDurationList("test_durations")).contains(Duration.ofMillis(10))
    }

    @Test
    fun `withOnlyPath still works`() {
        val conf = smartConfig.withOnlyPath("fred")
        assertThat(conf.hasPath("bob")).isFalse
        assertThat(conf.getString("fred")).isEqualTo("secret")
    }

    @Test
    fun `withoutPath still works`() {
        val conf = smartConfig.withoutPath("bob")
        assertThat(conf.hasPath("bob")).isFalse
        assertThat(conf.getString("fred")).isEqualTo("secret")
    }

    @Test
    fun `atPath still works`() {
        val conf = smartConfig.atPath("hello.world")
        assertThat(conf.getConfig("hello").getString("world.foo")).isEqualTo("bar")
        assertThat(conf.getConfig("hello").getString("world.fred")).isEqualTo("secret")
    }

    @Test
    fun `atKey still works`() {
        val conf = smartConfig.atKey("hello")
        assertThat(conf.getString("hello.foo")).isEqualTo("bar")
        assertThat(conf.getString("hello.fred")).isEqualTo("secret")
    }

    @Test
    fun `withValue still works`() {
        val moreConfig = smartConfig.withValue("rob", ConfigValueFactory.fromAnyRef("bob"))
        assertThat(moreConfig.getString("rob")).isEqualTo("bob")
    }

    @Test
    fun `equals compares underlying config`() {
        assertThat(smartConfig).isEqualTo(SmartConfigImpl(config, secretsLookupService))
    }

    @Test
    fun `equals between smart and typesafe config`() {
        // NOTE: confirm this is correct expected behaviour
        assertThat(smartConfig).isEqualTo(config)
    }

    @Test
    fun `withFallback still works when fallback is smart`() {
        val smartFallback = SmartConfigImpl(fallbackConfig, secretsLookupService)
        val c = smartConfig
            .withFallback(smartFallback)

        assertThat(c.getString("jon")).isEqualTo("fallback-secret")
    }

    enum class Snack {
        DONUTS,
        BISCUITS
    }
}