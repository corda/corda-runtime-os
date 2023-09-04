package net.corda.testing.driver.tests;

import java.util.List;
import java.util.Set;
import net.corda.testing.driver.DriverNodes;
import net.corda.testing.driver.EachTestDriver;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.virtualnode.VirtualNodeInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static net.corda.testing.driver.node.MemberStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static java.util.concurrent.TimeUnit.MINUTES;

@Timeout(value = 5, unit = MINUTES)
@TestInstance(PER_CLASS)
class DriverJavaTest {
    private static final MemberX500Name ALICE = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB");
    private static final MemberX500Name BOB = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US");
    private static final MemberX500Name LUCY = MemberX500Name.parse("CN=Lucy, OU=Testing, O=R3, L=Rome, C=IT");
    private static final MemberX500Name LUCY_WORKER = MemberX500Name.parse("CN=Lucy(Worker), OU=Testing, O=R3, L=Rome, C=IT");
    private static final MemberX500Name ZAPHOD = MemberX500Name.parse("CN=Zaphod, OU=Testing, O=HGTTG, L=Sirius, C=BG");

    @SuppressWarnings("JUnitMalformedDeclaration")
    @RegisterExtension
    private final EachTestDriver driver = new DriverNodes(ALICE, BOB).withNotary(LUCY, 1).forEachTest();

    @BeforeAll
    void sanityCheck() {
        // Ensure that we use the corda-driver bundle rather than a directory of its classes.
        assertThat(DriverNodes.class.getProtectionDomain().getCodeSource().getLocation().getPath()).endsWith(".jar");
    }

    @Test
    void testStartingAliceNodes() {
        driver.run(dsl -> {
            final List<VirtualNodeInfo> aliceNodes = dsl.startNodes(Set.of(ALICE));
            assertThat(aliceNodes).hasSize(2);

            assertThat(dsl.nodesFor("mandelbrot"))
                .hasEntrySatisfying(ALICE, vNode -> assertThat(aliceNodes).contains(vNode))
                .doesNotContainKeys(BOB, LUCY, LUCY_WORKER);

            assertThat(dsl.nodesFor("extendable-cpb"))
                .hasEntrySatisfying(ALICE, vNode -> assertThat(aliceNodes).contains(vNode))
                .doesNotContainKeys(BOB, LUCY, LUCY_WORKER);

            dsl.node("mandelbrot", ALICE, alice ->
                assertThat(alice.getStatus()).isEqualTo(ACTIVE)
            );
            dsl.node("extendable-cpb", ALICE, alice ->
                assertThat(alice.getStatus()).isEqualTo(ACTIVE)
            );

            dsl.group("mandelbrot", group -> {
                assertThat(group.members()).containsExactlyInAnyOrder(ALICE, LUCY_WORKER);
                group.member(LUCY_WORKER, lucyWorker ->
                    assertThat(lucyWorker.getStatus()).isEqualTo(ACTIVE)
                );
            });
            dsl.group("extendable-cpb", group -> {
                assertThat(group.members()).containsExactlyInAnyOrder(ALICE, LUCY_WORKER);
                group.member(LUCY_WORKER, lucyWorker ->
                    assertThat(lucyWorker.getStatus()).isEqualTo(ACTIVE)
                );
            });
        });
    }

    @Test
    void testStartingWithZaphodNodes() {
        final Throwable ex = driver.let(dsl ->
            assertThrows(IllegalArgumentException.class, () -> dsl.startNodes(Set.of(BOB, ZAPHOD)))
        );
        assertThat(ex)
            .hasMessageStartingWith("Non-member X500 names: ")
            .hasMessageContaining("CN=Zaphod,")
            .hasMessageNotContaining("CN=Bob,");
    }
}
