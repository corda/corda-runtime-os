package net.corda.e2etest.utilities

object AdminPasswordUtil {
    const val adminUser = "admin"
    val adminPassword = System.getenv("REST_API_ADMIN") ?: "admin"
}