package net.corda.applications.workers.e2etestutils.utils

object AdminPasswordUtil {
    const val adminUser = "admin"
    val adminPassword = System.getenv("INITIAL_ADMIN_USER_PASSWORD") ?: "admin"
}