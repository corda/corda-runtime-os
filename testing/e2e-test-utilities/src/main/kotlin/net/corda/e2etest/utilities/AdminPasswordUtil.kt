package net.corda.e2etest.utilities

object AdminPasswordUtil {
    const val adminUser = "admin"
    val adminPassword = System.getenv("INITIAL_ADMIN_USER_PASSWORD") ?: "admin"
}