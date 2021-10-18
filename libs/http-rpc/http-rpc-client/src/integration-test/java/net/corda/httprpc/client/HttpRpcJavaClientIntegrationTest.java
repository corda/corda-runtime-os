package net.corda.httprpc.client;

import net.corda.httprpc.client.config.HttpRpcClientConfig;
import net.corda.httprpc.server.config.models.HttpRpcSettings;
import net.corda.httprpc.server.impl.HttpRpcServerImpl;
import net.corda.httprpc.test.CustomSerializationAPIImpl;
import net.corda.httprpc.test.TestHealthCheckAPI;
import net.corda.httprpc.test.TestHealthCheckAPIImpl;
import net.corda.v5.base.util.NetworkHostAndPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.corda.httprpc.test.utls.TestUtilsKt.findFreePort;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpRpcJavaClientIntegrationTest extends HttpRpcIntegrationTestBase {
    static int port = -1;

    @BeforeAll
    static void setUpBeforeClass() {
        port = findFreePort();
        HttpRpcSettings httpRpcSettings = new HttpRpcSettings(new NetworkHostAndPort("localhost", port),
            HttpRpcIntegrationTestBase.Companion.getContext(),
            null,
            null,
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
        );
        HttpRpcIntegrationTestBase.Companion.setServer(
            new HttpRpcServerImpl(
                List.of(
                    new TestHealthCheckAPIImpl(), new CustomSerializationAPIImpl()
                ), HttpRpcIntegrationTestBase.Companion.getSecurityManager(), httpRpcSettings, true
            )
        );
        HttpRpcIntegrationTestBase.Companion.getServer().start();
    }

    @AfterAll
    static void cleanUpAfterClass() {
        HttpRpcIntegrationTestBase.Companion.getServer().stop();
    }

    @Test
    void start_connection_aware_client_from_java_against_server_with_accepted_protocol_version_succeeds() {
        HttpRpcClient<TestHealthCheckAPI> client = new HttpRpcClient<>(
            "http://localhost:" + port + "/api/v1/",
            TestHealthCheckAPI.class,
            new HttpRpcClientConfig().enableSSL(false).minimumServerProtocolVersion(1)
                .username(HttpRpcIntegrationTestBase.Companion.getUserAlice().getUsername())
                .password(HttpRpcIntegrationTestBase.Companion.getUserAlice().getPassword())
        );

        try (client) {
            HttpRpcConnection<TestHealthCheckAPI> connection = client.start();
            TestHealthCheckAPI proxy = connection.getProxy();
            assertEquals(3, proxy.plus(2L));
            assertDoesNotThrow(() -> proxy.voidResponse());
            assertEquals("\"Pong for str = value\"", proxy.ping(new TestHealthCheckAPI.PingPongData("value")));
            assertEquals(List.of(2.0, 3.0, 4.0), proxy.plusOne(List.of("1", "2", "3")));
        }
    }
}
