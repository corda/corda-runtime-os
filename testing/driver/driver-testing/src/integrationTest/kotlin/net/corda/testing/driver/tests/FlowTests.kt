package net.corda.testing.driver.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.corda.testing.smoketests.flow.RpcSmokeTestFlow
import com.r3.corda.testing.smoketests.flow.messages.RpcSmokeTestInput
import java.util.UUID
import java.util.concurrent.TimeUnit.MINUTES
import net.corda.testing.driver.AllTestsDriver
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Suppress("FunctionName", "JUnitMalformedDeclaration")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class FlowTests {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
    private val jsonMapper = ObjectMapper()

    @RegisterExtension
    private val driver = AllTestsDriver(alice, bob)

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
    fun `create an initiated session in an initiating flow and pass it to a inline subflow`() {
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "subflow_passed_in_initiated_session"
                    data = mapOf(
                        "sessions" to "$alice;$bob",
                        "messages" to "m1;m2"
                    )
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Result={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("subflow_passed_in_initiated_session", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("$alice=echo:m1; $bob=echo:m2", result) }
    }

    @Test
    fun `create an uninitiated session in an initiating flow and pass it to a inline subflow`() {
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "subflow_passed_in_non_initiated_session"
                    data = mapOf(
                        "sessions" to "$alice;$bob",
                        "messages" to "m1;m2"
                    )
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Result={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("subflow_passed_in_non_initiated_session", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("$alice=echo:m1; $bob=echo:m2", result) }
    }

    @Test
    fun `initiate multiple sessions and exercise the flow messaging apis`() {
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "flow_messaging_apis"
                    data = mapOf("sessions" to bob.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Flow Messaging APIs result={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("flow_messaging_apis", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("$bob=Completed. Sum:18", result) }
    }

    @Test
    fun `crypto - sign and verify bytes`() {
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "crypto_sign_and_verify"
                    data = mapOf("memberX500" to alice.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Sign and Verify result={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("crypto_sign_and_verify", command) }
            .hasEntrySatisfying("result") { result -> assertEquals(true.toString(), result) }
    }

    @Test
    fun `crypto - verify invalid signature`() {
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "crypto_verify_invalid_signature"
                    data = mapOf("memberX500" to alice.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Verify Invalid Signature result={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("crypto_verify_invalid_signature", command) }
            .hasEntrySatisfying("result") { result -> assertEquals(true.toString(), result) }
    }

    @Test
    fun `serialize and deserialize an object`() {
        val dataToSerialize = "serialize this"
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "serialization"
                    data = mapOf("data" to dataToSerialize)
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Serialization result={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("serialization", command) }
            .hasEntrySatisfying("result") { result -> assertEquals(dataToSerialize, result) }
    }

    @Test
    fun `json serialization`() {
        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "json_serialization"
                    data = mapOf("vnode" to bob.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("JSON Serialization result={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command ->
                assertEquals("json_serialization", command)
            }.hasEntrySatisfying("result") { result ->
                assertThat(jsonMapper.readAsMap(result as String))
                    .hasEntrySatisfying("firstTest") { first ->
                        @Suppress("unchecked_cast")
                        assertThat(first as Map<String, *>)
                            .hasEntrySatisfying("serialized-implicitly") { value ->
                                assertEquals("combined-test-stringtest-string", value)
                            }
                    }.hasEntrySatisfying("secondTest") { second ->
                        assertEquals(bob.toString(), second)
                    }
            }
    }

    @Test
    fun `persist a single entity`() {
        val id = UUID.randomUUID()
        val flowResult = persistDog(id)
        logger.info("Persist Single Entity={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_persist", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("dog '$id' saved", result) }
    }

    @Test
    fun `persist multiples entities`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_persist_bulk"
                    data = mapOf("ids" to "$id1;$id2")
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Persist Multiple Entities={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_persist_bulk", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("dogs ${listOf(id1, id2)} saved", result) }
    }

    @Test
    fun `merge a single entity`() {
        val id = UUID.randomUUID()
        persistDog(id)

        val flowResult = mergeDog(id, "dog2")
        logger.info("Merge Single Entity={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_merge", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("dog '$id' merged", result) }
    }

    @Test
    fun `merge multiple entities`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        persistDog(id1)
        persistDog(id2)

        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_merge_bulk"
                    data = mapOf(
                        "ids" to "$id1;$id2",
                        "name" to "dog2"
                    )
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Merge Multiple Entities={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_merge_bulk", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("dogs ${listOf(id1, id2)} merged", result) }
    }

    @Test
    fun `delete a single entity`() {
        val id = UUID.randomUUID()
        persistDog(id)

        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_delete"
                    data = mapOf("id" to id.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Delete Single Entity={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_delete", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("dog '$id' deleted", result) }
    }

    @Test
    fun `delete multiple entities`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        persistDog(id1)
        persistDog(id2)

        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_delete_bulk"
                    data = mapOf("ids" to "$id1;$id2")
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Delete Multiple Entities={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_delete_bulk", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("dogs ${listOf(id1, id2)} deleted", result) }
    }

    @Test
    fun `find a single entity`() {
        val id = UUID.randomUUID()
        val newName = "new name"
        persistDog(id)
        mergeDog(id, newName)

        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_find"
                    data = mapOf("id" to id.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Find Single Entity={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_find", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("found dog id='$id' name='$newName'", result) }
    }

    @Test
    fun `find multiple entities`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val newName1 = "name one"
        val newName2 = "name two"
        persistDog(id1)
        persistDog(id2)
        mergeDog(id1, newName1)
        mergeDog(id2, newName2)

        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_find_bulk"
                    data = mapOf("ids" to "$id1;$id2")
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Find Multiple Entities={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command ->
                assertEquals("persistence_find_bulk", command)
            }.hasEntrySatisfying("result") { result ->
                assertThat(result as String)
                    .startsWith("found dogs ")
                    .contains("id='$id1' name='$newName1'")
                    .contains("id='$id2' name='$newName2'")
            }
    }

    @Test
    fun `find all entities`() {
        persistDog(UUID.randomUUID())

        val flowResult = driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_findall"
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("Find All Entities={}", flowResult)

        assertThat(jsonMapper.readAsMap(flowResult))
            .hasEntrySatisfying("command") { command -> assertEquals("persistence_findall", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("found one or more dogs", result) }
    }

    private fun persistDog(id: UUID): String {
        return driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_persist"
                    data = mapOf("id" to id.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("persistDog result must not be null")
    }

    private fun mergeDog(id: UUID, name: String): String {
        return driver.let { dsl ->
            dsl.runFlow(aliceCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "persistence_merge"
                    data = mapOf(
                        "id" to id.toString(),
                        "name" to name
                    )
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("mergeDog result must not be null")
    }
}
