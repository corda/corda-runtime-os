package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.User
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.permissions.password.impl.PasswordServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors

class RbacBasicAuthenticationServicePerfTest {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val userLogons: List<String> = (1..10).map { "user$it" }
        const val repsCount = 1_000
        const val PERF_THRESHOLD_MS = 10_000L
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
        assertFalse(rbacBasicAuthenticationService.authenticateUser(userLogons.first(), "wrongPassword".toCharArray()))

        // Measure time
        val start = System.currentTimeMillis()
        val result: List<Boolean> = (1..repsCount).map { iterCount ->  rbacBasicAuthenticationService.authenticateUser(
            userLogons[iterCount % userLogons.size],
            passwordString.toCharArray()
        )}
        val end = System.currentTimeMillis()

        assertThat(result).allMatch { true }

        val elapsedMs = end - start
        logger.info("singleThreadedTest elapsed time: $elapsedMs ms")

        assertThat(elapsedMs).isLessThan(PERF_THRESHOLD_MS)
    }

    @Test
    fun multiThreadedTest() {
        // Warm-up
        assertTrue(rbacBasicAuthenticationService.authenticateUser(userLogons.first(), passwordString.toCharArray()))
        assertFalse(rbacBasicAuthenticationService.authenticateUser(userLogons.first(), "wrongPassword".toCharArray()))

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

        val elapsedMs = end - start
        logger.info("multiThreadedTest elapsed time: $elapsedMs ms")

        assertThat(elapsedMs).isLessThan(PERF_THRESHOLD_MS)
    }
}