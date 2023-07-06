package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRpcFlow
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IonTests {
    private companion object {
        const val ION_TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val ION_TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"
    }

    private val groupId = UUID.randomUUID().toString()
    private val applicationCpiName = "${ION_TEST_CPI_NAME}"
    private val notaryCpiName = "${ION_TEST_CPB_LOCATION}"
    private val tinkerX500 = "CN=Tinker, OU=Application, O=R3, L=London, C=GB"
    private val tailorX500 = "CN=Tailor, OU=Application, O=R3, L=London, C=GB"
    private val soldierX500 = "CN=Soldier, OU=Application, O=R3, L=London, C=GB"
    private val spyX500 = "CN=Spy, OU=Application, O=R3, L=London, C=GB"
    private val notaryX500 = "C=GB, L=London, O=Notary"
    private var tinkerHoldingId: String = ""
    private var tailorHoldingId: String = ""
    private var soldierHoldingId: String = ""
    private var spyHoldingId: String = ""
    private var notaryHoldingId: String = ""
    private val staticMemberList = listOf(
        tinkerX500,
        tailorX500,
        soldierX500,
        spyX500,
        notaryX500
    )

    @Test
    @Order(1)
    fun setup() {
        // Upload test flows if not already uploaded
        conditionallyUploadCordaPackage(
            applicationCpiName, ION_TEST_CPB_LOCATION, groupId, staticMemberList
        )
        // Upload notary server CPB
        conditionallyUploadCordaPackage(
            notaryCpiName,
            TEST_NOTARY_CPB_LOCATION,
            groupId,
            staticMemberList
        )

        // Make sure Virtual Nodes are created
        tinkerHoldingId = getOrCreateVirtualNodeFor(tinkerX500, applicationCpiName)
        tailorHoldingId = getOrCreateVirtualNodeFor(tailorX500, applicationCpiName)
        soldierHoldingId = getOrCreateVirtualNodeFor(soldierX500, applicationCpiName)
        spyHoldingId = getOrCreateVirtualNodeFor(spyX500, applicationCpiName)
        notaryHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        registerStaticMember(tinkerHoldingId)
        registerStaticMember(tailorHoldingId)
        registerStaticMember(soldierHoldingId)
        registerStaticMember(spyHoldingId)
        registerStaticMember(notaryHoldingId, isNotary = true)
    }

    @Test
    @Order(1)
    fun start_one_flow() {

        tinkerHoldingId = getOrCreateVirtualNodeFor(tinkerX500, applicationCpiName)
        tailorHoldingId = getOrCreateVirtualNodeFor(tailorX500, applicationCpiName)
        soldierHoldingId = getOrCreateVirtualNodeFor(soldierX500, applicationCpiName)
        spyHoldingId = getOrCreateVirtualNodeFor(spyX500, applicationCpiName)
        notaryHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        val requestBody = mapOf(
            "tradeStatus" to "Proposed",
            "shrQty" to "25",
            "securityID" to "USCA765248",
            "settlementAmt" to "15",
            "deliver" to "CN=Tinker, OU=Application, O=R3, L=London, C=GB",
            "receiver" to "CN=Tailor, OU=Application, O=R3, L=London, C=GB",
            "settlementDelivererId" to "delivererIdXXXX",
            "settlementReceiverID" to "receiverIDXXXX",
            "dtcc" to "CN=Soldier, OU=TestDept, O=R3, L=NewYork, C=US",
            "dtccObserver" to "CN=Spy, OU=Application, O=R3, L=London, C=GB",
            "setlmntCrncyCd" to "sid1",
            "sttlmentlnsDlvrrRefId" to "ref1",
            "linearId" to UUID.randomUUID().toString()
        )

        val requestId = startRpcFlow(
            tinkerHoldingId,
            requestBody,
            "com.r3.corda.testing.smoketests.ion.workflows.CreateBilateralFlow"
        )
        println("Flow started with requestId='$requestId'...")

        val result = awaitRpcFlowFinished(tinkerHoldingId, requestId)
        println("Flow completed with result:'${result.flowStatus}' '${result.flowResult}' '${result.flowError}'")

        Assertions.assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        Assertions.assertThat(result.flowError).isNull()
    }
}