package net.corda.applications.workers.smoketest.stam

import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_ALIAS
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_USAGE
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import java.util.UUID

class StamTests {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            cluster {
                assertWithRetry {
                    // Certificate upload can be slow in the combined worker, especially after it has just started up.
                    timeout(120.seconds)
                    interval(1.seconds)
                    command { importCertificate(CODE_SIGNER_CERT, CODE_SIGNER_CERT_USAGE, CODE_SIGNER_CERT_ALIAS) }
                    condition { it.code == 204 }
                }
            }
        }
    }

    @RepeatedTest(8)
    fun createGroup(info: RepetitionInfo) {
        val testRunUniqueId = UUID.randomUUID()
        val name = "Application-${info.currentRepetition}"
        val groupId = UUID.randomUUID().toString()
        val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        val aliceX500 = "CN=Alice-${testRunUniqueId}, OU=$name, O=R3, L=London, C=GB"
        val bobX500 = "CN=Bob-${testRunUniqueId}, OU=$name, O=R3, L=London, C=GB"
        val charlieX500 = "CN=Charlie-${testRunUniqueId}, OU=$name, O=R3, L=London, C=GB"
        val aliceHoldingId = getHoldingIdShortHash(aliceX500, groupId)
        val bobHoldingId = getHoldingIdShortHash(bobX500, groupId)
        val charlieHoldingId = getHoldingIdShortHash(charlieX500, groupId)
        val staticMemberList = listOf(
            aliceX500,
            bobX500,
            charlieX500
        )
        println("QQQ staticMemberList $staticMemberList")
        println("QQQ cpiName $cpiName")
        println("QQQ aliceHoldingId $aliceHoldingId")
        println("QQQ charlieHoldingId $charlieHoldingId")
        println("QQQ bobHoldingId $bobHoldingId")
        conditionallyUploadCordaPackage(
            cpiName,
            TEST_CPB_LOCATION,
            groupId,
            staticMemberList
        )
        val aliceActualHoldingId = getOrCreateVirtualNodeFor(aliceX500, cpiName)
        val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
        val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlieX500, cpiName)

        assertThat(aliceActualHoldingId).isEqualTo(aliceHoldingId)
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)

        registerStaticMember(aliceHoldingId)
        registerStaticMember(bobHoldingId)
        registerStaticMember(charlieHoldingId)

    }
}