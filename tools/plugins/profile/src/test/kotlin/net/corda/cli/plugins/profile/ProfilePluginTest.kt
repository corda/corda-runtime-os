package net.corda.cli.plugins.profile

import net.corda.cli.plugins.profile.commands.CreateProfile
import net.corda.cli.plugins.profile.commands.DeleteProfile
import net.corda.cli.plugins.profile.commands.ListProfile
import net.corda.cli.plugins.profile.commands.UpdateProfile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.assertContains

class ProfilePluginTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var profileFilePath: Path
    private val originalOut = System.out
    private val outContent = ByteArrayOutputStream()

    @BeforeEach
    fun setup() {
        profileFilePath = tempDir.resolve("profile.yaml")
        ProfileUtils.profileFile = profileFilePath.toFile()
        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
        outContent.reset()
    }

    @Test
    fun `test create profile`() {
        val createProfile = CreateProfile()
        createProfile.profileName = "test-profile"
        createProfile.properties = arrayOf("endpoint=http://localhost:1234", "restUsername=testuser", "restPassword=testpassword")

        createProfile.run()

        assertTrue(profileFilePath.toFile().exists(), "Profile file should be created")
        assertEquals("Profile 'test-profile' created successfully.", outContent.toString().trim())

        val profiles = ProfileUtils.loadProfiles()
        assertTrue(profiles.containsKey("test-profile"), "Created profile should exist")
        val profile = profiles["test-profile"] as Map<*, *>
        assertEquals("http://localhost:1234", profile["endpoint"])
        assertEquals("testuser", profile["restUsername"])
        assertNotEquals("testpassword", profile["restPassword"], "Password should be encrypted")
    }

    @Test
    fun `test update profile`() {
        // Setup a test profile
        val profiles = mutableMapOf<String, Any>(
            "test-profile" to mapOf(
                "endpoint" to "http://localhost:1234",
                "restUsername" to "testuser",
                "restPassword" to "testpassword"
            )
        )
        ProfileUtils.saveProfiles(profiles)

        val updateProfile = UpdateProfile()
        updateProfile.profileName = "test-profile"
        updateProfile.properties = arrayOf("restUsername=updateduser", "restPassword=updatedpassword")

        updateProfile.run()

        assertEquals("Profile 'test-profile' updated successfully.", outContent.toString().trim())

        val updatedProfiles = ProfileUtils.loadProfiles()
        val profile = updatedProfiles["test-profile"] as Map<*, *>
        assertEquals("updateduser", profile["restUsername"])
        assertNotEquals("updatedpassword", profile["restPassword"], "Password should be encrypted")
    }

    @Test
    fun `test delete profile`() {
        // Setup test profile
        val profiles = mutableMapOf<String, Any>(
            "test-profile" to mapOf(
                "endpoint" to "http://localhost:1234",
                "restUsername" to "testuser",
                "restPassword" to "testpassword"
            )
        )
        ProfileUtils.saveProfiles(profiles)

        val deleteProfile = DeleteProfile()
        deleteProfile.profileName = "test-profile"

        System.setIn("y".byteInputStream())

        deleteProfile.run()

        assertContains(outContent.toString().trim(), "Profile 'test-profile' deleted.")

        val updatedProfiles = ProfileUtils.loadProfiles()
        assertFalse(updatedProfiles.containsKey("test-profile"), "Deleted profile should not exist")
    }

    @Test
    fun `test list profiles`() {
        // Create test profiles
        val profiles = mutableMapOf<String, Any>(
            "profile1" to mapOf("endpoint" to "http://localhost:1234"),
            "profile2" to mapOf("endpoint" to "http://localhost:5678")
        )
        ProfileUtils.saveProfiles(profiles)

        val listProfile = ListProfile()
        listProfile.run()

        val expectedOutput = """
            Available profiles:
            - profile1
            - profile2
        """.trimIndent().trim()
        assertEquals(expectedOutput, outContent.toString().trimIndent().trim())
    }
}