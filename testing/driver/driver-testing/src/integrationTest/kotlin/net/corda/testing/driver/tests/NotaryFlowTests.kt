package net.corda.testing.driver.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.corda.testing.testflows.NonValidatingNotaryTestFlow
import java.util.concurrent.TimeUnit.MINUTES
import net.corda.testing.driver.DriverNodes
import net.corda.testing.driver.node.FlowErrorException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Suppress("FunctionName", "JUnitMalformedDeclaration")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class NotaryFlowTests {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
    private val notary = MemberX500Name.parse("CN=Notary, OU=Testing, O=R3, L=Rome, C=IT")
    private val jsonMapper = ObjectMapper()

    @RegisterExtension
    private val driver = DriverNodes(alice, bob).withNotary(notary, 1, 2).forAllTests()

    private lateinit var aliceCorDapp: VirtualNodeInfo

    @BeforeAll
    fun start() {
        aliceCorDapp = driver.let { dsl ->
            dsl.startNode(setOf(alice, bob)).onEach { vNode ->
                logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
            }.single { vNode ->
                vNode.cpiIdentifier.name == "test-cordapp" && vNode.holdingIdentity.x500Name == alice
            }
        }
        logger.info("{} and {} started successfully", alice.commonName, bob.commonName)
    }

    @Test
    fun `non-validating plugin executes successfully when using issuance transaction`() {
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = IssueStatesParameters(outputStateCount = 3)
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Issuance Transaction={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("issuedStateRefs") { assertThat(it).asList().hasSize(3) }
            .hasEntrySatisfying("consumedInputStateRefs") { assertThat(it).asList().isEmpty() }
            .hasEntrySatisfying("consumedReferenceStateRefs") { assertThat(it).asList().isEmpty() }
    }

    @Test
    fun `non-validating plugin returns error when time window invalid`() {
        val ex = assertThrows<FlowErrorException> {
            driver.let { dsl ->
                dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                    val request = IssueStatesParameters(
                        outputStateCount = 3,
                        timeWindowLowerBoundOffsetMs = -2000,
                        timeWindowUpperBoundOffsetMs = -1000
                    )
                    jsonMapper.writeValueAsString(request)
                }
            }
        }

        assertThat(ex)
            .hasMessageStartingWith("Unable to notarize transaction ")
            .hasMessageContaining(" Time Window Out of Bounds.")
    }

    @Test
    fun `non-validating plugin executes successfully and returns signatures when consuming a valid transaction`() {
        // 1. Issue 1 state
        val issuanceFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = IssueStatesParameters(outputStateCount = 1)
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("issuanceFlowResult must not be null")
        logger.info("Issuance={}", issuanceFlowResult)

        val issuanceResult = jsonMapper.readAsMap(issuanceFlowResult)
        assertThat(issuanceResult)
            // 2. Make sure the states were issued
            .hasEntrySatisfying("issuedStateRefs") { assertThat(it).asList().hasSize(1) }

            // 3. Make sure no states were consumed
            .hasEntrySatisfying("consumedInputStateRefs") { assertThat(it).asList().isEmpty() }
            .hasEntrySatisfying("consumedReferenceStateRefs") { assertThat(it).asList().isEmpty() }

        @Suppress("unchecked_cast")
        val issuedStates = issuanceResult["issuedStateRefs"] as List<String>

        // 4. Consume one of the issued states as an input state
        val consumeFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = ConsumeStatesParameters(issuedStates)
                writeAsFlatMap(request)
            }
        } ?: fail("consumeFlowResult must not be null")
        logger.info("Consume={}", consumeFlowResult)

        // 5. Make sure only one input state was consumed, and nothing was issued
        assertThat(jsonMapper.readAsMap(consumeFlowResult))
            .hasEntrySatisfying("issuedStateRefs") { assertThat(it).asList().isEmpty() }
            .hasEntrySatisfying("consumedInputStateRefs") { assertThat(it).asList().hasSize(1) }
            .hasEntrySatisfying("consumedReferenceStateRefs") { assertThat(it).asList().isEmpty() }
    }

    @Test
    fun `non-validating plugin returns error on double spend`() {
        // 1. Issue 1 state
        val issuanceFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = IssueStatesParameters(outputStateCount = 1)
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("issuanceFlowResult must not be null")
        logger.info("Issuance={}", issuanceFlowResult)

        val issuanceResult = jsonMapper.readAsMap(issuanceFlowResult)
        assertThat(issuanceResult)
            // 2. Make sure the states were issued
            .hasEntrySatisfying("issuedStateRefs") { assertThat(it).asList().hasSize(1) }

            // 3. Make sure no states were consumed
            .hasEntrySatisfying("consumedInputStateRefs") { assertThat(it).asList().isEmpty() }
            .hasEntrySatisfying("consumedReferenceStateRefs") { assertThat(it).asList().isEmpty() }

        // 4. Spend the issued state
        @Suppress("unchecked_cast")
        val toConsume = issuanceResult["issuedStateRefs"] as List<String>

        val firstConsumeFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = ConsumeStatesParameters(toConsume)
                writeAsFlatMap(request)
            }
        } ?: fail("firstConsumeFlowResult must not be null")
        logger.info("First Consume={}", firstConsumeFlowResult)

        // 5. Try to spend the state again, and expect the error
        val ex = assertThrows<FlowErrorException> {
            driver.let { dsl ->
                dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                    val request = ConsumeStatesParameters(toConsume)
                    writeAsFlatMap(request)
                }
            }
        }

        assertThat(ex)
            .hasMessageStartingWith("Unable to notarize transaction ")
            .hasMessageContaining("Input State Conflict(s): ")
    }

    @Test
    fun `non-validating plugin returns error when trying to spend unknown reference state`() {
        // Random unknown StateRef
        val unknownStateRef = "SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903:0"

        // 1. Issue 1 state
        val issuanceFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = IssueStatesParameters(outputStateCount = 1)
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("issuanceFlowResult must not be null")
        logger.info("Issuance={}", issuanceFlowResult)

        val issuanceResult = jsonMapper.readAsMap(issuanceFlowResult)
        assertThat(issuanceResult)
            // 2. Make sure the states were issued
            .hasEntrySatisfying("issuedStateRefs") { assertThat(it).asList().hasSize(1) }

            // 3. Make sure no states were consumed
            .hasEntrySatisfying("consumedInputStateRefs") { assertThat(it).asList().isEmpty() }
            .hasEntrySatisfying("consumedReferenceStateRefs") { assertThat(it).asList().isEmpty() }

        @Suppress("unchecked_cast")
        val issuedStates = issuanceResult["issuedStateRefs"] as List<String>

        // 4. Spend a valid state and reference an unknown state
        val ex = assertThrows<FlowErrorException> {
            driver.let { dsl ->
                dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                    val request = ConsumeStatesParameters(
                        inputStateRefs = issuedStates,
                        referenceStateRefs = listOf(unknownStateRef)
                    )
                    writeAsFlatMap(request)
                }
            }
        }

        assertThat(ex)
            .hasMessageStartingWith("Could not find StateRef $unknownStateRef ")
            .hasMessageContaining(" when resolving reference states.")
    }

    @Test
    fun `non-validating plugin returns error when using the same state for input and ref`() {
        // 1. Issue 1 state
        val issuanceFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = IssueStatesParameters(outputStateCount = 1)
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("issuanceFlowResult must not be null")
        logger.info("Issuance={}", issuanceFlowResult)

        val issuanceResult = jsonMapper.readAsMap(issuanceFlowResult)
        assertThat(issuanceResult)
            // 2. Make sure the states were issued
            .hasEntrySatisfying("issuedStateRefs") { assertThat(it).asList().hasSize(1) }

            // 3. Make sure no states were consumed
            .hasEntrySatisfying("consumedInputStateRefs") { assertThat(it).asList().isEmpty() }
            .hasEntrySatisfying("consumedReferenceStateRefs") { assertThat(it).asList().isEmpty() }

        @Suppress("unchecked_cast")
        val issuedStates = issuanceResult["issuedStateRefs"] as List<String>

        // 4. Make sure we consumed the state and managed to reference it
        // Since the state we are trying to spend and reference is not spent yet (not persisted) we should be able
        // to spend it and reference at the same time
        val ex = assertThrows<FlowErrorException> {
            driver.let { dsl ->
                dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                    val request = ConsumeStatesParameters(
                        inputStateRefs = issuedStates,
                        referenceStateRefs = issuedStates
                    )
                    writeAsFlatMap(request)
                }
            }
        }

        assertThat(ex)
            .hasMessageStartingWith("A state cannot be both an input and a reference input in the same transaction.")
            .hasMessageContaining("Offending states: $issuedStates")
    }

    @Test
    fun `non-validating plugin returns error when trying to spend unknown input state`() {
        // Random unknown StateRef
        val unknownStateRef = "SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903:0"

        val ex = assertThrows<FlowErrorException> {
            driver.let { dsl ->
                dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                    val request = ConsumeStatesParameters(
                        inputStateRefs = listOf(unknownStateRef),
                        referenceStateRefs = emptyList()
                    )
                    writeAsFlatMap(request)
                }
            }
        }

        assertThat(ex)
            .hasMessageStartingWith("Could not find StateRef $unknownStateRef when resolving input states.")
    }

    @Test
    fun `non-validating plugin returns error when referencing spent state`() {
        // 1. Issue 2 states
        val issuanceFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = IssueStatesParameters(outputStateCount = 2)
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("issuanceFlowResult must not be null")
        logger.info("Issuance={}", issuanceFlowResult)

        val issuanceResult = jsonMapper.readAsMap(issuanceFlowResult)
        assertThat(issuanceResult)
            // 2. Make sure the states were issued
            .hasEntrySatisfying("issuedStateRefs") { assertThat(it).asList().hasSize(2) }

            // 3. Make sure no states were consumed
            .hasEntrySatisfying("consumedInputStateRefs") { assertThat(it).asList().isEmpty() }
            .hasEntrySatisfying("consumedReferenceStateRefs") { assertThat(it).asList().isEmpty() }

        @Suppress("unchecked_cast")
        val issuedStates = issuanceResult["issuedStateRefs"] as List<String>

        // 4. Spend the issued state
        val firstConsumeFlowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                val request = ConsumeStatesParameters(
                    inputStateRefs = listOf(issuedStates[0]),
                    referenceStateRefs = emptyList()
                )
                writeAsFlatMap(request)
            }
        } ?: fail("firstConsumeFlowResult must not be null")
        logger.info("First Consume={}", firstConsumeFlowResult)

        // 5. Try to reference the spent state.
        // Note we MUST spend or issue a state otherwise it is not a valid transaction. In this case we choose to spend
        // it because it's easier to do with `consumeStatesAndValidateResult`.
        val ex = assertThrows<FlowErrorException> {
            driver.let { dsl ->
                dsl.runFlow(aliceCorDapp, NonValidatingNotaryTestFlow::class.java) {
                    val request = ConsumeStatesParameters(
                        inputStateRefs = listOf(issuedStates[1]),
                        referenceStateRefs = listOf(issuedStates[0])
                    )
                    writeAsFlatMap(request)
                }
            }
        }

        assertThat(ex)
            .hasMessageStartingWith("Unable to notarize transaction ")
            .hasMessageContaining(" Reference State Conflict(s): ")
    }

    private fun writeAsFlatMap(request: ConsumeStatesParameters): String {
        return jsonMapper.writeValueAsString(mapOf(
            "inputStateRefs" to jsonMapper.writeValueAsString(request.inputStateRefs),
            "referenceStateRefs" to jsonMapper.writeValueAsString(request.referenceStateRefs)
        ))
    }

    data class IssueStatesParameters(
        val outputStateCount: Int,
        val timeWindowLowerBoundOffsetMs: Long? = null,
        val timeWindowUpperBoundOffsetMs: Long? = null
    )

    data class ConsumeStatesParameters(
        val inputStateRefs: List<String>,
        val referenceStateRefs: List<String> = emptyList()
    )
}
