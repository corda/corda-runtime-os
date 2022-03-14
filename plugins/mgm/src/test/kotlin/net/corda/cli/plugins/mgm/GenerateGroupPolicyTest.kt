package net.corda.cli.plugins.mgm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrAndOutNormalized
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path

class GenerateGroupPolicyTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `command generates and prints GroupPolicy to output as correctly formatted JSON`() {
        val app = GenerateGroupPolicy()

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute()
        }.apply {
            assertNotEquals(0, this.length)
            assertTrue(isValidJson(this))

            val om = jacksonObjectMapper()
            val parsed = om.readValue<Map<String, Any>>(this)

            assertEquals(1, parsed["fileFormatVersion"])
        }
    }

    @Test
    fun `when no parameters are passed, the default group policy is returned`() {
        val app = GenerateGroupPolicy()

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute()
        }.apply {
            val memberList = memberList(this)
            memberList.find { it["name"].asText()=="C=GB, L=London, O=Alice" }.apply {
                assertEquals("alice-alias", this?.get("keyAlias")?.asText())
                assertEquals("alice-historic-alias-1", this?.get("rotatedKeyAlias-1")?.asText())
                assertEquals("ACTIVE", this?.get("memberStatus")?.asText())
                assertEquals("https://alice.corda5.r3.com:10000", this?.get("endpointUrl-1")?.asText())
                assertEquals("1", this?.get("endpointProtocol-1")?.asText())
            }

            assertNotNull(memberList.find { it["name"].asText()=="C=GB, L=London, O=Bob" })
            assertNotNull(memberList.find { it["name"].asText()=="C=GB, L=London, O=Charlie" })
        }
    }

    @Test
    fun `when member name is specified, endpoint information is required`() {
        tapSystemErrAndOutNormalized {
            CommandLine(GenerateGroupPolicy()).execute("--name=XYZ")
        }.apply {
            assertTrue(this.contains("Endpoint must be specified using '--endpoint'."))
        }

        tapSystemErrAndOutNormalized {
            CommandLine(GenerateGroupPolicy()).execute("--name=XYZ", "--endpoint=dummy")
        }.apply {
            assertTrue(this.contains("Endpoint protocol must be specified using '--endpoint-protocol'."))
        }

        tapSystemErrAndOutNormalized {
            CommandLine(GenerateGroupPolicy()).execute("--name=XYZ", "--endpoint-protocol=5")
        }.apply {
            assertTrue(this.contains("Endpoint must be specified using '--endpoint'."))
        }
    }

    @Test
    fun `when endpoint information is specified without member names, group policy with empty member list is returned`() {
        val app = GenerateGroupPolicy()

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute(
                "--endpoint=http://dummy-url",
                "--endpoint-protocol=5"
            )
        }.apply {
            assertTrue(
                this.contains("\"members\" : [ ]")
            )
        }
    }

    @Test
    fun `string parameters are correctly parsed to generate group policy with specified names and endpoint information`() {
        val app = GenerateGroupPolicy()

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute(
                "--name=C=GB, L=London, O=Member1",
                "--name=C=GB, L=London, O=Member2",
                "--endpoint=http://dummy-url",
                "--endpoint-protocol=5"
            )
        }.apply {
            memberList(this).forEach {
                assertTrue(it["name"].asText().contains("C=GB, L=London, O=Member"))
                assertTrue(it["keyAlias"].asText().contains("C=GB, L=London, O=Member"))
                assertTrue(it["rotatedKeyAlias-1"].asText().contains("C=GB, L=London, O=Member"))
                assertEquals("ACTIVE", it["memberStatus"].asText())
                assertEquals("http://dummy-url", it["endpointUrl-1"].asText())
                assertEquals("5", it["endpointProtocol-1"].asText())
            }
        }
    }

    @Test
    fun `specifying file input and string parameters together throws exception`() {
        val app = GenerateGroupPolicy()

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--name=XYZ", "--file=app/build/libs/src.json")
        }.apply {
            assertTrue(this.contains("Member name(s) may not be specified when '--file' is set."))
        }
    }

    @Test
    fun `if file type is not JSON or YAML (YML), exception is thrown`() {
        val app = GenerateGroupPolicy()
        val filePath = Files.createFile(tempDir.resolve("src.txt"))
        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath")
        }.apply {
            assertTrue(this.contains("Input file format not supported."))
        }
    }

    @Test
    fun `JSON file with 'members' is correctly parsed to generate group policy with specified member information`() {
        val app = GenerateGroupPolicy()
        val filePath = Files.createFile(tempDir.resolve("src.json"))
        filePath.toFile().writeText(
            "{\n" +
                    "  \"endpoint\": \"http://dummy-url\",\n" +
                    "  \"endpointProtocol\": 5,\n" +
                    "  \"members\": [\n" +
                    "    {\n" +
                    "      \"name\": \"C=GB, L=London, O=Member1\",\n" +
                    "      \"status\": \"PENDING\",\n" +
                    "      \"endpointProtocol\": 10\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"name\": \"C=GB, L=London, O=Member2\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}"
        )

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath")
        }.apply {
            memberList(this).forEach {
                assertTrue(it["name"].asText().contains("C=GB, L=London, O=Member"))
                assertTrue(it["keyAlias"].asText().contains("C=GB, L=London, O=Member"))
                assertTrue(it["rotatedKeyAlias-1"].asText().contains("C=GB, L=London, O=Member"))
                assertTrue(it["memberStatus"].asText().contains("PENDING") || it["memberStatus"].asText().contains("ACTIVE"))
                assertEquals("http://dummy-url", it["endpointUrl-1"].asText())
            }
        }
    }

    @Test
    fun `YAML file with 'memberNames' is correctly parsed to generate group policy with specified member information`() {
        val app = GenerateGroupPolicy()
        val filePath = Files.createFile(tempDir.resolve("src.yaml"))
        filePath.toFile().writeText(
            "endpoint: \"http://dummy-url\"\n" +
                    "endpointProtocol: 5\n" +
                    "memberNames: [\"C=GB, L=London, O=Member1\", \"C=GB, L=London, O=Member2\"]\n"
        )

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath")
        }.apply {
            memberList(this).forEach {
                assertTrue(it["name"].asText().contains("C=GB, L=London, O=Member"))
                assertTrue(it["keyAlias"].asText().contains("C=GB, L=London, O=Member"))
                assertTrue(it["rotatedKeyAlias-1"].asText().contains("C=GB, L=London, O=Member"))
                assertEquals("ACTIVE", it["memberStatus"].asText())
                assertEquals("http://dummy-url", it["endpointUrl-1"].asText())
                assertEquals("5", it["endpointProtocol-1"].asText())
            }
        }
    }

    @Test
    fun `exception is thrown when the input file has both 'members' and 'memberNames' blocks`() {
        val app = GenerateGroupPolicy()
        val filePath = Files.createFile(tempDir.resolve("src.yaml"))
        filePath.toFile().writeText(
            "endpoint: \"http://dummy-url\"\n" +
                    "endpointProtocol: 5\n" +
                    "memberNames:\n" +
                    "  - \"C=GB, L=London, O=Member1\"\n" +
                    "  - \"C=GB, L=London, O=Member2\"\n" +
                    "members:\n" +
                    "  - member:\n" +
                    "      name: \"C=GB, L=London, O=Member1\"\n" +
                    "      status: \"PENDING\"\n" +
                    "      endpointProtocol: 10\n" +
                    "  - member:\n" +
                    "      name: \"C=GB, L=London, O=Member2\""
        )

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath")
        }.apply {
            assertTrue(this.contains("Only one of 'memberNames' and 'members' blocks may be specified."))
        }
    }

    @Test
    fun `exception is thrown when the input file is empty`() {
        val app = GenerateGroupPolicy()
        val filePath = Files.createFile(tempDir.resolve("src.yaml"))

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath")
        }.apply {
            assertTrue(this.contains("Could not read static network information from"))
        }
    }

    @Test
    fun `exception is thrown if file specifies 'members' or 'memberNames' but no endpoint information`() {
        val app = GenerateGroupPolicy()
        val filePath1 = Files.createFile(tempDir.resolve("src.yaml"))
        filePath1.toFile().writeText("memberNames: [\"C=GB, L=London, O=Member1\", \"C=GB, L=London, O=Member2\"]")

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath1")
        }.apply {
            assertTrue(this.contains("Endpoint must be specified."))
        }

        val filePath2 = Files.createFile(tempDir.resolve("src2.yaml"))
        filePath2.toFile().writeText(
            "members:\n" +
                    "    - name: \"C=GB, L=London, O=Member1\"\n" +
                    "      status: \"PENDING\""
        )

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath2")
        }.apply {
            assertTrue(this.contains("No endpoint specified."))
        }
    }

    private fun memberList(output: String): JsonNode {
        val mapper = ObjectMapper()
        return mapper.readTree(output)["protocolParameters"]["staticNetwork"]["members"]
    }

    /**
     * Checks that the [content] String is a valid JSON.
     */
    private fun isValidJson(content: String): Boolean {
        return try {
            jacksonObjectMapper().readTree(content)
            true
        } catch (e: Exception) {
            false
        }
    }
}