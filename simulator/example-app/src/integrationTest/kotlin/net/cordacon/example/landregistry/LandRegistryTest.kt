package net.cordacon.example.landregistry

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.cordacon.example.landregistry.flows.*
import net.cordacon.example.utils.createMember
import org.junit.jupiter.api.Test

class LandRegistryTest {

    @Test
    fun `issuer should issue land title to the owner`() {

        val simulator = Simulator()
        val issuer = createMember("Alice")
        val owner = createMember("Bob")

        val nodes = listOf(issuer, owner).map {
            val node = simulator.createVirtualNode(it, IssueLandTitleFlow::class.java,
                IssueLandTitleResponderFlow::class.java)
            node.generateKey("${it.commonName}-key", HsmCategory.LEDGER, "any-scheme")
            node
        }

        val request =  LandRegistryRequest(
            "T001",
            "BKC, Mumbai",
            500,
            "Awesome Property",
            owner
        )

        val requestData = RequestData.create(
            "r1",
            IssueLandTitleFlow::class.java,
            request
        )

        val result = nodes[0].callFlow(requestData)

        println(result)

    }

    @Test
    fun `should transfer land title to another owner`(){
        val simulator = Simulator()
        val issuer = createMember("Alice")
        val owner = createMember("Bob")
        val newOwner = createMember("Charlie")

        val nodes = listOf(issuer, owner, newOwner).map {
            val node = simulator.createVirtualNode(it, IssueLandTitleFlow::class.java,
                IssueLandTitleResponderFlow::class.java, TransferLandTitleFlow::class.java,
                TransferLandTitleResponderFlow::class.java)
            node.generateKey("${it.commonName}-key", HsmCategory.LEDGER, "any-scheme")
            node
        }

        val issueRequest =  LandRegistryRequest(
            "T001",
            "BKC, Mumbai",
            500,
            "Awesome Property",
            owner
        )

        val issueRequestData = RequestData.create(
            "r1",
            IssueLandTitleFlow::class.java,
            issueRequest
        )

        val result = nodes[0].callFlow(issueRequestData)
        println(result)

        val transferRequest = TransferLandTitleRequest(
            "T001",
            newOwner
        )

        val transferRequestData = RequestData.create(
            "r2",
            TransferLandTitleFlow::class.java,
            transferRequest
        )

        val result1 = nodes[1].callFlow(transferRequestData)

        println(result1)
    }
}