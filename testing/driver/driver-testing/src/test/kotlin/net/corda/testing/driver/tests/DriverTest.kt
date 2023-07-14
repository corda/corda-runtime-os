package net.corda.testing.driver.tests

import java.util.concurrent.TimeUnit.MINUTES
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverNodes
import net.corda.testing.driver.node.MemberStatus
import net.corda.testing.driver.node.MemberStatus.ACTIVE
import net.corda.testing.driver.node.MemberStatus.PENDING
import net.corda.testing.driver.node.MemberStatus.SUSPENDED
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Suppress("JUnitMalformedDeclaration")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class DriverTest {
    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
    private val lucy = MemberX500Name.parse("CN=Lucy, OU=Testing, O=R3, L=Rome, C=IT")
    private val zaphod = MemberX500Name.parse("CN=Zaphod, OU=Testing, O=HGTTG, L=Sirius, C=BG")
    private val logger = LoggerFactory.getLogger(DriverTest::class.java)

    @RegisterExtension
    private val driver = DriverNodes(alice, bob).withNotary(lucy, 1).forEachTest()

    @BeforeAll
    fun sanityCheck() {
        // Ensure that we use the corda-driver bundle rather than a directory of its classes.
        assertThat(DriverNodes::class.java.protectionDomain.codeSource.location.path).endsWith(".jar")
    }

    private fun DriverDSL.testNodesFor(member: MemberX500Name): Map<String, VirtualNodeInfo> {
        return startNodes(setOf(member)).onEach { vNode ->
            logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
        }.associateBy { vNode ->
            vNode.cpiIdentifier.name
        }.also { nodes ->
            assertThat(nodes).hasSize(2)
            logger.info("{} started successfully", member.commonName)
        }
    }

    @Test
    fun testNodesAndStatus() {
        driver.run { dsl ->
            dsl.testNodesFor(alice).values.forEach { aliceNode ->
                dsl.groupOf(aliceNode) { group ->
                    assertThat(group.members())
                        .containsExactlyInAnyOrder(alice, bob, lucy)

                    // Suspend Lucy's notary.
                    group.member(lucy) { notary ->
                        assertEquals(ACTIVE, notary.status)

                        notary.status = SUSPENDED
                        assertEquals(SUSPENDED, notary.status)
                    }

                    // Set Bob to pending...
                    group.member(bob) {
                        it.status = PENDING
                    }

                    // Group membership is unaffected by these status changes.
                    assertThat(group.members())
                        .containsExactlyInAnyOrder(alice, bob, lucy)
                }
            }

            // Check Bob knows he is now pending.
            dsl.testNodesFor(bob).values.forEach { bobNode ->
                dsl.member(bobNode) { node ->
                    assertEquals(PENDING, node.status)
                }

                dsl.groupOf(bobNode) { group ->
                    // Check Bob knows that Lucy's notary is also suspended.
                    group.member(lucy) { notary ->
                        assertEquals(SUSPENDED, notary.status)
                    }

                    // Check Zaphod is NOT part of this group!
                    assertThrows<AssertionError> {
                        group.member(zaphod) {}
                    }
                }
            }
        }
    }

    private fun DriverDSL.assertGroupMemberStatus(vNode: VirtualNodeInfo, expectedStatus: MemberStatus) {
        groupOf(vNode) { group ->
            assertThat(group.members()).containsExactlyInAnyOrder(alice, bob, lucy)

            group.members().forEach { x500 ->
                group.member(x500) {
                    assertEquals(expectedStatus, it.status)
                }
            }
        }
    }

    private fun DriverDSL.setGroupMemberStatus(vNode: VirtualNodeInfo, newStatus: MemberStatus) {
        groupOf(vNode) { group ->
            assertThat(group.members()).containsExactlyInAnyOrder(alice, bob, lucy)

            group.members().forEach { x500 ->
                group.member(x500) { member ->
                    assertNotEquals(newStatus, member.status)
                    member.status = newStatus
                }
            }
        }
    }

    @Test
    fun testGroupsAreIndependent() {
        driver.run { dsl ->
            val aliceNodes = dsl.testNodesFor(alice)
            val bobNodes = dsl.testNodesFor(bob)

            val cordappNames = ArrayList(aliceNodes.keys)
            assertThat(bobNodes.keys)
                .containsExactlyInAnyOrderElementsOf(cordappNames)
                .hasSizeGreaterThanOrEqualTo(2)

            val firstCordapp = cordappNames[0]
            val firstAliceNode = aliceNodes[firstCordapp] ?: fail("Unknown CorDapp $firstCordapp for Alice")
            dsl.setGroupMemberStatus(firstAliceNode, SUSPENDED)

            val firstBobNode = bobNodes[firstCordapp] ?: fail("Unknown CorDapp $firstCordapp for Bob")
            dsl.assertGroupMemberStatus(firstBobNode, SUSPENDED)

            val secondCordapp = cordappNames[1]
            val secondAliceNode = aliceNodes[secondCordapp] ?: fail("Unknown CorDapp $secondCordapp for Alice")
            dsl.assertGroupMemberStatus(secondAliceNode, ACTIVE)

            val secondBobNode = bobNodes[secondCordapp] ?: fail("Unknown CorDapp $secondCordapp for Bob")
            dsl.assertGroupMemberStatus(secondBobNode, ACTIVE)
        }
    }
}
