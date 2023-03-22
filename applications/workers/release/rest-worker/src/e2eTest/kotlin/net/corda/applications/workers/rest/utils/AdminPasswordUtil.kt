package net.corda.applications.workers.rest.utils

object AdminPasswordUtil {
    const val adminUser = "admin"
    val adminPassword = System.getenv("REST_API_ADMIN_PASSWORD") ?: "admin"
}