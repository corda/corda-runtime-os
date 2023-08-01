package net.corda.rest.client;

import net.corda.rest.client.config.RestClientConfig;
import net.corda.rest.server.config.models.RestServerSettings;
import net.corda.rest.server.impl.RestServerImpl;
import net.corda.rest.test.CustomSerializationAPIImpl;
import net.corda.rest.test.TestHealthCheckAPI;
import net.corda.rest.test.TestHealthCheckAPIImpl;
import net.corda.utilities.NetworkHostAndPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestJavaClientIntegrationTest extends RestIntegrationTestBase {

    @BeforeAll
    static void setUpBeforeClass() {
        RestServerSettings restServerSettings = new RestServerSettings(new NetworkHostAndPort("localhost", 0),
            RestIntegrationTestBase.Companion.getContext(),
            null,
            null,
            RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        );
        RestIntegrationTestBase.Companion.setServer(
                new RestServerImpl(
                        List.of(new TestHealthCheckAPIImpl(), new CustomSerializationAPIImpl()),
                        RestIntegrationTestBase.Companion::getSecurityManager,
                        restServerSettings,
                        Path.of(System.getProperty("java.io.tmpdir")),
                        true
                )
        );
        RestIntegrationTestBase.Companion.getServer().start();
    }

    @AfterAll
    static void cleanUpAfterClass() {
        RestIntegrationTestBase.Companion.getServer().close();
    }

    @Test
    void start_connection_aware_client_from_java_against_server_with_accepted_protocol_version_succeeds() {
        RestClient<TestHealthCheckAPI> client = new RestClient<>(
            "http://localhost:" + RestIntegrationTestBase.Companion.getServer().getPort() + "/api/" +
                    RestIntegrationTestBase.Companion.getApiVersion().getVersionPath() + "/",
            TestHealthCheckAPI.class,
            new RestClientConfig().enableSSL(false).minimumServerProtocolVersion(1)
                .username(RestIntegrationTestBase.Companion.getUserAlice().getUsername())
                .password(Objects.requireNonNull(RestIntegrationTestBase.Companion.getUserAlice().getPassword()))
        );

        try (client) {
            RestConnection<TestHealthCheckAPI> connection = client.start();
            TestHealthCheckAPI proxy = connection.getProxy();
            assertEquals(3, proxy.plus(2L));
            assertDoesNotThrow(proxy::voidResponse);
            assertEquals("Pong for str = value", proxy.ping(new TestHealthCheckAPI.PingPongData("value")));
            assertEquals(List.of(2.0, 3.0, 4.0), proxy.plusOne(List.of("1", "2", "3")));
        }
    }
}
