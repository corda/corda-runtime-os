package net.corda.crypto.component.config

import com.typesafe.config.ConfigFactory
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.data.crypto.config.CryptoConfigurationRecord
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MemberConfigWriterTests {
    private lateinit var cryptoMemberConfig: CryptoMemberConfig
    private lateinit var pub: Publisher
    private lateinit var writer: MemberConfigWriterImpl
    private lateinit var records: KArgumentCaptor<List<Record<String, CryptoConfigurationRecord>>>

    @BeforeEach
    fun setup() {
        cryptoMemberConfig = CryptoMemberConfigImpl(
            mapOf(
                "default" to mapOf(
                    "serviceName" to "default",
                    "timeout" to "1",
                    "defaultSignatureScheme" to "CORDA.ECDSA.SECP256K1",
                    "serviceConfig" to mapOf(
                        "passphrase" to "pwdD",
                        "salt" to "saltD"
                    )
                ),
                "LEDGER" to mapOf(
                    "serviceName" to "FUTUREX",
                    "timeout" to "10",
                    "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
                    "serviceConfig" to mapOf(
                        "credentials" to "password"
                    )
                )
            )
        )
        pub = mock {
            on { publish(any()) }.thenReturn(
                listOf(
                    CompletableFuture<Unit>().also { it.complete(Unit) }
                )
            )
        }
        val factory: PublisherFactory = mock {
            on { createPublisher(any(), any()) }.thenReturn(pub)
        }
        writer = MemberConfigWriterImpl(factory)
        records = argumentCaptor()
    }

    private fun initializeWriter() {
        writer.start()
        val config = CryptoLibraryConfigImpl(
            mapOf(
                "memberConfig" to mapOf(
                    "groupName" to "group",
                    "topicName" to "topic",
                    "clientId" to "client"
                )
            )
        )
        writer.handleConfigEvent(config)
    }

    @Test
    @Timeout(5)
    fun `Should throw IllegalStateException if writer is not configured yet`() {
        writer.start()
        assertFailsWith<IllegalStateException> {
            writer.put("123", cryptoMemberConfig)
        }
    }

    @Test
    @Timeout(5)
    fun `Should publish config changes`() {
        initializeWriter()
        val before = Instant.now()
        writer.put("123", cryptoMemberConfig)
        val after = Instant.now()
        Mockito.verify(pub, times(1)).start()
        Mockito.verify(pub, times(1)).publish(records.capture())
        assertEquals(1, records.allValues.size)
        assertEquals(1, records.firstValue.size)
        val record = records.firstValue[0]
        assertEquals("123", record.key)
        assertNotNull(record.value)
        assertEquals(1, record.value!!.version)
        assertThat(
            record.value!!.timestamp.epochSecond,
            allOf(greaterThanOrEqualTo(before.epochSecond), lessThanOrEqualTo(after.epochSecond))
        )
        val publishedConfig = ConfigFactory.parseString(record.value!!.value).root().unwrapped()
        assertEquals(2, publishedConfig.size)
        assertThat(publishedConfig.keys, contains("default", "LEDGER"))
        writer.stop()
    }

    @Test
    @Timeout(5)
    fun `Should close initialized publishers`() {
        initializeWriter()
        Mockito.verify(pub, never()).close()
        writer.stop()
        Mockito.verify(pub, times(1)).close()
    }

    @Test
    @Timeout(5)
    fun `Should close old publisher and create new`() {
        initializeWriter()
        Mockito.verify(pub, never()).close()
        initializeWriter()
        Mockito.verify(pub, times(1)).close()
        writer.stop()
        Mockito.verify(pub, times(2)).close()
    }
}