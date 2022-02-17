package net.corda.cli.plugins.mgm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrAndOutNormalized
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue

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
            assertTrue(
                this.contains(
                    "{\n" +
                            "        \"name\" : \"C=GB, L=London, O=Alice\",\n" +
                            "        \"keyAlias\" : \"alice-alias\",\n" +
                            "        \"rotatedKeyAlias-1\" : \"alice-historic-alias-1\",\n" +
                            "        \"memberStatus\" : \"ACTIVE\",\n" +
                            "        \"endpointUrl-1\" : \"https://alice.corda5.r3.com:10000\",\n" +
                            "        \"endpointProtocol-1\" : 1\n" +
                            "      }"
                )
            )
        }
    }

    @Test
    fun `when member name is specified, endpoint information is required`() {
        tapSystemErrAndOutNormalized {
            CommandLine(GenerateGroupPolicy()).execute("--name=XYZ")
        }.apply {
            assertTrue(this.contains("Endpoint URL must be specified using '--endpoint-url'."))
        }

        tapSystemErrAndOutNormalized {
            CommandLine(GenerateGroupPolicy()).execute("--name=XYZ", "--endpoint-url=dummy")
        }.apply {
            assertTrue(this.contains("Endpoint protocol must be specified using '--endpoint-protocol'."))
        }

        tapSystemErrAndOutNormalized {
            CommandLine(GenerateGroupPolicy()).execute("--name=XYZ", "--endpoint-protocol=5")
        }.apply {
            assertTrue(this.contains("Endpoint URL must be specified using '--endpoint-url'."))
        }
    }

    @Test
    fun `when endpoint information is specified without member names, group policy with empty member list is returned`() {
        val app = GenerateGroupPolicy()

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute(
                "--endpoint-url=http://dummy-url",
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
                "--endpoint-url=http://dummy-url",
                "--endpoint-protocol=5"
            )
        }.apply {
            assertTrue(
                this.contains(
                    "\"members\" : [\n" +
                            "      {\n" +
                            "        \"name\" : \"C=GB, L=London, O=Member1\",\n" +
                            "        \"keyAlias\" : \"alias\",\n" +
                            "        \"rotatedKeyAlias-1\" : \"historic-alias-1\",\n" +
                            "        \"memberStatus\" : \"ACTIVE\",\n" +
                            "        \"endpointUrl-1\" : \"http://dummy-url\",\n" +
                            "        \"endpointProtocol-1\" : 5\n" +
                            "      },\n" +
                            "      {\n" +
                            "        \"name\" : \"C=GB, L=London, O=Member2\",\n" +
                            "        \"keyAlias\" : \"alias\",\n" +
                            "        \"rotatedKeyAlias-1\" : \"historic-alias-1\",\n" +
                            "        \"memberStatus\" : \"ACTIVE\",\n" +
                            "        \"endpointUrl-1\" : \"http://dummy-url\",\n" +
                            "        \"endpointProtocol-1\" : 5\n" +
                            "      }\n" +
                            "    ]"
                )
            )
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
                    "  \"endpointUrl\": \"http://dummy-url\",\n" +
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
            assertTrue(
                this.contains(
                    "\"members\" : [\n" +
                            "      {\n" +
                            "        \"name\" : \"C=GB, L=London, O=Member1\",\n" +
                            "        \"keyAlias\" : \"alias\",\n" +
                            "        \"rotatedKeyAlias-1\" : \"historic-alias-1\",\n" +
                            "        \"memberStatus\" : \"PENDING\",\n" +
                            "        \"endpointUrl-1\" : \"http://dummy-url\",\n" +
                            "        \"endpointProtocol-1\" : 10\n" +
                            "      },\n" +
                            "      {\n" +
                            "        \"name\" : \"C=GB, L=London, O=Member2\",\n" +
                            "        \"keyAlias\" : \"alias\",\n" +
                            "        \"rotatedKeyAlias-1\" : \"historic-alias-1\",\n" +
                            "        \"memberStatus\" : \"ACTIVE\",\n" +
                            "        \"endpointUrl-1\" : \"http://dummy-url\",\n" +
                            "        \"endpointProtocol-1\" : 5\n" +
                            "      }\n" +
                            "    ]"
                )
            )
        }
    }

    @Test
    fun `YAML file with 'memberNames' is correctly parsed to generate group policy with specified member information`() {
        val app = GenerateGroupPolicy()
        val filePath = Files.createFile(tempDir.resolve("src.yaml"))
        filePath.toFile().writeText(
            "endpointUrl: \"http://dummy-url\"\n" +
                    "endpointProtocol: 5\n" +
                    "memberNames: [\"C=GB, L=London, O=Member1\", \"C=GB, L=London, O=Member2\"]\n"
        )

        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--file=$filePath")
        }.apply {
            assertTrue(
                this.contains(
                    "\"members\" : [\n" +
                            "      {\n" +
                            "        \"name\" : \"C=GB, L=London, O=Member1\",\n" +
                            "        \"keyAlias\" : \"alias\",\n" +
                            "        \"rotatedKeyAlias-1\" : \"historic-alias-1\",\n" +
                            "        \"memberStatus\" : \"ACTIVE\",\n" +
                            "        \"endpointUrl-1\" : \"http://dummy-url\",\n" +
                            "        \"endpointProtocol-1\" : 5\n" +
                            "      },\n" +
                            "      {\n" +
                            "        \"name\" : \"C=GB, L=London, O=Member2\",\n" +
                            "        \"keyAlias\" : \"alias\",\n" +
                            "        \"rotatedKeyAlias-1\" : \"historic-alias-1\",\n" +
                            "        \"memberStatus\" : \"ACTIVE\",\n" +
                            "        \"endpointUrl-1\" : \"http://dummy-url\",\n" +
                            "        \"endpointProtocol-1\" : 5\n" +
                            "      }\n" +
                            "    ]"
                )
            )
        }
    }

    @Test
    fun `exception is thrown when the input file has both 'members' and 'memberNames' blocks`() {
        val app = GenerateGroupPolicy()
        val filePath = Files.createFile(tempDir.resolve("src.yaml"))
        filePath.toFile().writeText(
            "endpointUrl: \"http://dummy-url\"\n" +
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
            assertTrue(this.contains("Endpoint URL must be specified."))
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
            assertTrue(this.contains("No endpoint URL specified."))
        }
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