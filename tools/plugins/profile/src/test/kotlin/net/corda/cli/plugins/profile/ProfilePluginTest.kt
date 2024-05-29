package net.corda.cli.plugins.profile

import net.corda.cli.plugins.profile.commands.CreateProfile
import net.corda.cli.plugins.profile.commands.DeleteProfile
import net.corda.cli.plugins.profile.commands.ListProfile
import net.corda.cli.plugins.profile.commands.UpdateProfile
import net.corda.libs.configuration.secret.SecretEncryptionUtil
import net.corda.sdk.profile.CliProfile
import net.corda.sdk.profile.ProfileUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.UUID
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
        ProfileUtils.initialize(profileFilePath.toFile())
        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    fun cleanup() {
        System.setOut(originalOut)
    }

    @Test
    fun `test create profile`() {
        val createProfile = CreateProfile()
        createProfile.profileName = "test-profile"
        createProfile.properties = arrayOf("rest_endpoint=http://localhost:1234", "rest_username=testuser", "rest_password=testpassword")

        createProfile.run()

        val profiles = ProfileUtils.loadProfiles()
        assertTrue(profiles.containsKey("test-profile"), "Created profile should exist")
        val profileProperties = profiles["test-profile"]!!.properties
        assertEquals("http://localhost:1234", profileProperties["rest_endpoint"])
        assertEquals("testuser", profileProperties["rest_username"])
        assertNotEquals("testpassword", profileProperties["rest_password"], "Password should be encrypted")
    }

    @Test
    fun `test create profile with existing name`() {
        val profiles = mutableMapOf(
            "test-profile" to CliProfile(
                mapOf(
                    "endpoint" to "http://localhost:1234",
                    "rest_username" to "testuser",
                    "rest_password" to "testpassword",
                    "rest_password_salt" to "testsalt"
                )
            )
        )
        ProfileUtils.saveProfiles(profiles)

        val createProfile = CreateProfile()
        createProfile.profileName = "test-profile"
        createProfile.properties = arrayOf(
            "rest_endpoint=http://localhost:5678",
            "rest_username=newuser",
            "rest_password=newpassword",
        )

        // user input to overwrite the existing profile
        System.setIn(ByteArrayInputStream("y\n".toByteArray()))

        createProfile.run()

        val updatedProfiles = ProfileUtils.loadProfiles()
        val profileProperties = updatedProfiles["test-profile"]!!.properties
        assertEquals("http://localhost:5678", profileProperties["rest_endpoint"])
        assertEquals("newuser", profileProperties["rest_username"])
        assertNotEquals("newpassword", profileProperties["rest_password"], "Password should be encrypted")
    }

    @Test
    fun `test create profile with invalid key`() {
        val createProfile = CreateProfile()
        createProfile.profileName = "test-profile"
        createProfile.properties = arrayOf("rest_endpoint=http://localhost:1234", "invalid_key=value")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            createProfile.run()
        }

        assertContains(exception.message.toString(), "Invalid key 'invalid_key'. Allowed keys are:")
        assertFalse(profileFilePath.toFile().exists(), "Profile file should not be created")
    }

    @Test
    fun `test update profile`() {
        // Setup a test profile
        val secretEncryptionUtil = SecretEncryptionUtil()
        val salt = UUID.randomUUID().toString()

        val profiles = mutableMapOf(
            "test-profile" to CliProfile(
                mapOf(
                    "rest_endpoint" to "http://localhost:1234",
                    "rest_username" to "testuser",
                    "rest_password" to secretEncryptionUtil.encrypt("testpassword", salt, salt),
                    "rest_password_salt" to salt
                )
            )
        )
        ProfileUtils.saveProfiles(profiles)

        val updateProfile = UpdateProfile()
        updateProfile.profileName = "test-profile"
        updateProfile.properties = arrayOf(
            "rest_username=updateduser",
            "rest_password=updatedpassword",
        )

        updateProfile.run()

        val updatedProfiles = ProfileUtils.loadProfiles()
        val profileProperties = updatedProfiles["test-profile"]!!.properties
        assertEquals("updateduser", profileProperties["rest_username"])
        assertNotEquals("updatedpassword", profileProperties["rest_password"], "Password should be encrypted")
    }

    @Test
    fun `test delete profile`() {
        // Setup test profile
        val secretEncryptionUtil = SecretEncryptionUtil()
        val salt = UUID.randomUUID().toString()

        val profiles = mutableMapOf(
            "test-profile" to CliProfile(
                mapOf(
                    "rest_endpoint" to "http://localhost:1234",
                    "rest_username" to "testuser",
                    "rest_password" to secretEncryptionUtil.encrypt("testpassword", salt, salt),
                    "rest_password_salt" to salt
                )
            )
        )
        ProfileUtils.saveProfiles(profiles)

        val deleteProfile = DeleteProfile()
        deleteProfile.profileName = "test-profile"

        // Simulate user input for confirmation
        System.setIn(ByteArrayInputStream("y\n".toByteArray()))

        deleteProfile.run()

        val updatedProfiles = ProfileUtils.loadProfiles()
        assertFalse(updatedProfiles.containsKey("test-profile"), "Deleted profile should not exist")
    }

    @Test
    fun `test list profiles`() {
        // Create test profiles
        val profiles = mutableMapOf(
            "profile1" to CliProfile(mapOf("rest_endpoint" to "http://localhost:1234")),
            "profile2" to CliProfile(mapOf("rest_endpoint" to "http://localhost:5678"))
        )
        ProfileUtils.saveProfiles(profiles)

        val listProfile = ListProfile()
        listProfile.run()

        val loadedProfiles = ProfileUtils.loadProfiles()
        assertEquals(2, loadedProfiles.size)
        assertTrue(loadedProfiles.containsKey("profile1"))
        assertTrue(loadedProfiles.containsKey("profile2"))
    }
}
