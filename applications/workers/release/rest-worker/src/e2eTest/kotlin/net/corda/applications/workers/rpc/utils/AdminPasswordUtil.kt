package net.corda.applications.workers.rpc.utils

object AdminPasswordUtil {
    const val adminUser = "admin"
    val adminPassword = System.getenv("INITIAL_ADMIN_USER_PASSWORD") ?: "admin"
}