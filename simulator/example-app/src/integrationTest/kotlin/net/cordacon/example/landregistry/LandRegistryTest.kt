package net.cordacon.example.landregistry

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.factories.JsonMarshallingServiceFactory
import net.corda.v5.application.marshalling.parse
import net.cordacon.example.landregistry.flows.*
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LandRegistryTest {

    private val jsonMarshallingService = JsonMarshallingServiceFactory.create()
    private val issuer = createMember("Alice")
    private val owner = createMember("Bob")
    private val newOwner = createMember("Charlie")

    @Test
    fun `issuer should issue land title to the owner`() {

        val simulator = Simulator()
        val nodes = listOf(issuer, owner).map {
            val node = simulator.createVirtualNode(it, IssueLandTitleFlow::class.java,
                IssueLandTitleResponderFlow::class.java, FetchLandTitleFlow::class.java)
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

        assertNotNull(result)

        val fetchRequestData = RequestData.create(
            "r2",
            FetchLandTitleFlow::class.java,
            Filter()
        )
        val issuerFetchResult = nodes[0].callFlow(fetchRequestData)
        val issuerLandTitle = jsonMarshallingService.parse<List<LinkedHashMap<String, String>>>(issuerFetchResult)
        assertThat(issuerLandTitle.size, `is`(1))
        assertThat(issuerLandTitle[0]["titleNumber"], `is`("T001"))
        assertThat(issuerLandTitle[0]["issuer"], `is`("CN=Alice, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))
        assertThat(issuerLandTitle[0]["owner"], `is`("CN=Bob, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))

        val ownerFetchResult = nodes[1].callFlow(fetchRequestData)
        val ownerLandTitle = jsonMarshallingService.parse<List<LinkedHashMap<String, String>>>(ownerFetchResult)
        assertThat(ownerLandTitle.size, `is`(1))
        assertThat(ownerLandTitle[0]["titleNumber"], `is`("T001"))
        assertThat(ownerLandTitle[0]["issuer"], `is`("CN=Alice, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))
        assertThat(ownerLandTitle[0]["owner"], `is`("CN=Bob, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))

    }

    @Test
    fun `should transfer land title to another owner`(){
        val simulator = Simulator()

        val nodes = listOf(issuer, owner, newOwner).map {
            val node = simulator.createVirtualNode(it, IssueLandTitleFlow::class.java,
                IssueLandTitleResponderFlow::class.java, TransferLandTitleFlow::class.java,
                TransferLandTitleResponderFlow::class.java, FetchLandTitleFlow::class.java)
            node.generateKey("${it.commonName}-key", HsmCategory.LEDGER, "any-scheme")
            node
        }

        val issueRequest =  LandRegistryRequest(
            "T002",
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

        val issueResult = nodes[0].callFlow(issueRequestData)
        assertNotNull(issueResult)

        val transferRequest = TransferLandTitleRequest(
            "T002",
            newOwner
        )

        val transferRequestData = RequestData.create(
            "r2",
            TransferLandTitleFlow::class.java,
            transferRequest
        )

        val transferResult = nodes[1].callFlow(transferRequestData)

        assertNotNull(transferResult)

        val fetchRequestData = RequestData.create(
            "r3",
            FetchLandTitleFlow::class.java,
            Filter("T002")
        )
        val issuerFetchResult = nodes[0].callFlow(fetchRequestData)
        val issuerLandTitle = jsonMarshallingService.parse<List<LinkedHashMap<String, String>>>(issuerFetchResult)
        assertThat(issuerLandTitle.size, `is`(1))
        assertThat(issuerLandTitle[0]["titleNumber"], `is`("T002"))
        assertThat(issuerLandTitle[0]["issuer"], `is`("CN=Alice, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))
        assertThat(issuerLandTitle[0]["owner"], `is`("CN=Charlie, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))

        val ownerFetchResult = nodes[1].callFlow(fetchRequestData)
        val ownerLandTitle = jsonMarshallingService.parse<List<LinkedHashMap<String, String>>>(ownerFetchResult)
        assertThat(ownerLandTitle.size, `is`(0))

        val newOwnerFetchResult = nodes[2].callFlow(fetchRequestData)
        val newOwnerLandTitle = jsonMarshallingService.parse<List<LinkedHashMap<String, String>>>(newOwnerFetchResult)
        assertThat(newOwnerLandTitle.size, `is`(1))
        assertThat(newOwnerLandTitle[0]["titleNumber"], `is`("T002"))
        assertThat(newOwnerLandTitle[0]["issuer"], `is`("CN=Alice, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))
        assertThat(newOwnerLandTitle[0]["owner"], `is`("CN=Charlie, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))

    }
}