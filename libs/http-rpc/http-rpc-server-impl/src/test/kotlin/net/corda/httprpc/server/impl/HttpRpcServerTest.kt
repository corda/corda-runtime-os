package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal.Companion.INSECURE_SERVER_DEV_MODE_WARNING
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal.Companion.SSL_PASSWORD_MISSING
import net.corda.httprpc.server.impl.security.SecurityManagerRPCImpl
import net.corda.v5.base.util.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.nio.file.Paths

class HttpRpcServerTest {

    private companion object {
        const val PORT = 8080 // doesn't matter, server won't start anyway
    }

    @Test
    fun `start server with ssl option but without ssl password specified throws illegal argument exception`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", PORT)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(Paths.get("my", "ssl", "keystore", "path")).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(false).whenever(configProvider).isDevModeEnabled()
        Assertions.assertThrows(
            IllegalArgumentException::class.java, {
                HttpRpcServerInternal(
                    SecurityManagerRPCImpl(emptySet()),
                    configProvider,
                    emptyList()
                )
            },
            SSL_PASSWORD_MISSING
        )
    }

    @Test
    fun `start server with ssl disabled but without dev mode enabled throws unsupported operation exception`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", PORT)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(false).whenever(configProvider).isDevModeEnabled()
        Assertions.assertThrows(
            Exception::class.java, {
                HttpRpcServerInternal(
                    SecurityManagerRPCImpl(emptySet()),
                    configProvider,
                    emptyList()
                )
            },
            INSECURE_SERVER_DEV_MODE_WARNING
        )
    }

    // Do we want to do anything with [CordaSerializable] classes that cannot be handled by Jackson?
    // Feel like JSON inputs and outputs should just be handled by Jackson and not have anything to do with [CordaSerializable]
    @Disabled
    @Test
    fun `start server with ssl disabled with dev mode enabled but non-CordaSerializable endpoint parameters throws exception`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", PORT)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(true).whenever(configProvider).isDevModeEnabled()
        assertThatThrownBy {
            HttpRpcServerInternal(
                SecurityManagerRPCImpl(emptySet()),
                configProvider,
                emptyList()
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
//            .hasMessage(
//                "Errors when validate resource classes:\n" +
//                        ParameterBodyCordaSerializableAnnotationValidator.error(NonCordaSerializableAPI::call.javaMethod!!, "data")
//            )
    }
}

