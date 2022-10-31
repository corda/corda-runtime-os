package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.User
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.permissions.password.impl.PasswordServiceImpl
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.SecureRandom
import java.time.Instant

import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors

class RbacBasicAuthenticationServicePerfTest {

    private companion object {
        val logger = contextLogger()
        val userLogons: List<String> = (1..10).map { "user$it" }
        const val repsCount = 1_000
    }


    private val passwordService = PasswordServiceImpl(SecureRandom())

    private val passwordString = "password"

    private val passwordHash = passwordService.saltAndHash(passwordString)

    private val permissionManagementCache = mock<PermissionManagementCache>().apply {
        whenever(this.getUser(any())).then { invocation ->
            val loginName = invocation.arguments.single() as String
            User(
                loginName,
                1,
                ChangeDetails(Instant.now()),
                loginName,
                "full name",
                true,
                passwordHash.value,
                passwordHash.salt,
                null,
                false,
                null,
                null,
                emptyList()
            )
        }
    }

    private val rbacBasicAuthenticationService =
        RbacBasicAuthenticationService(
            AtomicReference(permissionManagementCache),
            passwordService
        )

    @Test
    fun singleThreadedTest() {
        // Warm-up
        assertTrue(rbacBasicAuthenticationService.authenticateUser(userLogons.first(), passwordString.toCharArray()))

        // Measure time
        val start = System.currentTimeMillis()
        val result: List<Boolean> = (1..repsCount).map { iterCount ->  rbacBasicAuthenticationService.authenticateUser(
            userLogons[iterCount % userLogons.size],
            passwordString.toCharArray()
        )}
        val end = System.currentTimeMillis()

        assertThat(result).allMatch { true }

        logger.info("singleThreadedTest elapsed time: ${end - start} ms")
    }

    @Test
    fun multiThreadedTest() {
        // Warm-up
        assertTrue(rbacBasicAuthenticationService.authenticateUser(userLogons.first(), passwordString.toCharArray()))

        // Measure time
        val start = System.currentTimeMillis()
        val result: List<Boolean> = (1..repsCount).toList().parallelStream().map { iterCount ->
            rbacBasicAuthenticationService.authenticateUser(
                userLogons[iterCount % userLogons.size],
                passwordString.toCharArray()
            )
        }.collect(Collectors.toList())
        val end = System.currentTimeMillis()

        assertThat(result).allMatch { true }

        logger.info("multiThreadedTest elapsed time: ${end - start} ms")
    }
}