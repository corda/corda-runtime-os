package net.corda.testing.driver.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r3.corda.demo.mandelbrot.CalculateBlockFlow;
import com.r3.corda.demo.mandelbrot.RequestMessage;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.corda.testing.driver.AllTestsDriver;
import net.corda.testing.driver.DriverNodes;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.virtualnode.VirtualNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Timeout(value = 5, unit = MINUTES)
@TestInstance(PER_CLASS)
class FlowDriverJavaTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowDriverJavaTest.class);

    private static final MemberX500Name ALICE = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB");
    private static final MemberX500Name BOB = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US");
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @SuppressWarnings("JUnitMalformedDeclaration")
    @RegisterExtension
    private final AllTestsDriver driver = new DriverNodes(ALICE, BOB).forAllTests();

    @BeforeAll
    void start() {
        // Ensure that we use the corda-driver bundle rather than a directory of its classes.
        assertThat(DriverNodes.class.getProtectionDomain().getCodeSource().getLocation().getPath()).endsWith(".jar");

        driver.run(dsl ->
            dsl.startNodes(Set.of(ALICE, BOB)).forEach(vNode ->
                LOGGER.info("VirtualNode({}): {}", vNode.getHoldingIdentity().getX500Name(), vNode)
            )
        );
        LOGGER.info("{} and {} started successfully", ALICE.getCommonName(), BOB.getCommonName());
    }

    @ParameterizedTest
    @ArgumentsSource(RequestProvider.class)
    void testMandelbrotFlow(RequestMessage request) {
        final Map<MemberX500Name, VirtualNodeInfo> mandelbrot = driver.let(dsl ->
            dsl.nodesFor("mandelbrot")
        );

        final String aliceResult = driver.let(dsl ->
            dsl.runFlow(mandelbrot.get(ALICE), CalculateBlockFlow.class, () ->
                jsonMapper.writeValueAsString(request)
            )
        );
        assertNotNull(aliceResult, "aliceResult must not be null");
        LOGGER.info("Alice Mandelbrot Block={}", aliceResult);

        final String bobResult = driver.let(dsl ->
            dsl.runFlow(mandelbrot.get(BOB), CalculateBlockFlow.class, () ->
                jsonMapper.writeValueAsString(request)
            )
        );
        assertNotNull(bobResult, "bobResult must not be null");
        LOGGER.info("Bob Mandelbrot Block={}", bobResult);
    }

    private static class RequestProvider implements ArgumentsProvider {
        @Override
        @NotNull
        public Stream<? extends Arguments> provideArguments(@NotNull ExtensionContext context) {
            return Stream.of(
                Arguments.of(createRequestMessage(100.0, 20.2)),
                Arguments.of(createRequestMessage(253.7, -10.1)),
                Arguments.of(createRequestMessage(854.9, 120.6)),
                Arguments.of(createRequestMessage(-577.2, 88.8)),
                Arguments.of(createRequestMessage(14.5, 37.3))
            );
        }

        @NotNull
        private RequestMessage createRequestMessage(double startX, double startY) {
            final RequestMessage request = new RequestMessage();
            request.setStartX(startX);
            request.setStartY(startY);
            request.setWidth(50.0);
            request.setHeight(50.0);
            return request;
        }
    }
}
