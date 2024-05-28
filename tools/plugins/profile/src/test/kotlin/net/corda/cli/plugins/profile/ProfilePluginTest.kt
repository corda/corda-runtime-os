package net.corda.cli.plugins.profile

import net.corda.cli.plugins.profile.commands.CreateProfile
import net.corda.cli.plugins.profile.commands.DeleteProfile
import net.corda.cli.plugins.profile.commands.ListProfile
import net.corda.cli.plugins.profile.commands.UpdateProfile
import net.corda.libs.configuration.secret.SecretEncryptionUtil
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
        outContent.reset()
    }

    @Test
    fun `test create profile`() {
        val createProfile = CreateProfile()
        createProfile.profileName = "test-profile"
        createProfile.properties = arrayOf("rest_endpoint=http://localhost:1234", "rest_username=testuser", "rest_password=testpassword")

        createProfile.run()

        val profiles = ProfileUtils.loadProfiles()
        assertTrue(profiles.containsKey("test-profile"), "Created profile should exist")
        val profile = profiles["test-profile"] as Map<*, *>
        assertEquals("http://localhost:1234", profile["rest_endpoint"])
        assertEquals("testuser", profile["rest_username"])
        assertNotEquals("testpassword", profile["rest_password"], "Password should be encrypted")
    }

    @Test
    fun `test create profile with existing name`() {
        val profiles = mutableMapOf(
            "test-profile" to mapOf(
                "endpoint" to "http://localhost:1234",
                "rest_username" to "testuser",
                "rest_password" to "testpassword",
                "rest_password_salt" to "testsalt"
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
        val profile = updatedProfiles["test-profile"] as Map<*, *>
        assertEquals("http://localhost:5678", profile["rest_endpoint"])
        assertEquals("newuser", profile["rest_username"])
        assertNotEquals("newpassword", profile["rest_password"], "Password should be encrypted")
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
            "test-profile" to mapOf(
                "rest_endpoint" to "http://localhost:1234",
                "rest_username" to "testuser",
                "rest_password" to secretEncryptionUtil.encrypt("testpassword", salt, salt),
                "rest_password_salt" to salt
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
        val profile = updatedProfiles["test-profile"] as Map<*, *>
        assertEquals("updateduser", profile["rest_username"])
        assertNotEquals("updatedpassword", profile["rest_password"], "Password should be encrypted")
    }

    @Test
    fun `test idempotence of UpdateProfile with initial creation`() {
        val initialProfiles = ProfileUtils.loadProfiles()
        assertFalse(initialProfiles.containsKey("test-profile"))

        val updateProfile = UpdateProfile()
        updateProfile.profileName = "test-profile"
        updateProfile.properties = arrayOf(
            "rest_username=updateduser",
            "rest_password=updatedpassword",
        )

        // Run UpdateProfile command for the first time, should create the profile
        updateProfile.run()

        val updatedProfiles1 = ProfileUtils.loadProfiles()
        val profile1 = updatedProfiles1["test-profile"] as Map<*, *>

        // Run UpdateProfile command for the second time, should not change the profile
        updateProfile.run()

        val updatedProfiles2 = ProfileUtils.loadProfiles()
        val profile2 = updatedProfiles2["test-profile"] as Map<*, *>

        // Check that the state of the profile remains the same after the second run
        assertEquals(profile1, profile2)
    }

    @Test
    fun `test delete profile`() {
        // Setup test profile
        val secretEncryptionUtil = SecretEncryptionUtil()
        val salt = UUID.randomUUID().toString()

        val profiles = mutableMapOf(
            "test-profile" to mapOf(
                "rest_endpoint" to "http://localhost:1234",
                "rest_username" to "testuser",
                "rest_password" to secretEncryptionUtil.encrypt("testpassword", salt, salt),
                "rest_password_salt" to salt
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
    fun `test delete non-existing profile`() {
        val deleteProfile = DeleteProfile()
        deleteProfile.profileName = "non-existing-profile"

        val exception = assertThrows(IllegalArgumentException::class.java) {
            deleteProfile.run()
        }

        assertEquals("Profile 'non-existing-profile' does not exist.", exception.message)
    }

    @Test
    fun `test list profiles`() {
        // Create test profiles
        val profiles = mutableMapOf(
            "profile1" to mapOf("rest_endpoint" to "http://localhost:1234"),
            "profile2" to mapOf("rest_endpoint" to "http://localhost:5678")
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
