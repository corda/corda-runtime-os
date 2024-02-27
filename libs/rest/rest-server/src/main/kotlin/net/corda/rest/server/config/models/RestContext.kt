package net.corda.rest.server.config.models

data class RestContext(
    val basePath: String,
    val title: String,
    val description: String
)
