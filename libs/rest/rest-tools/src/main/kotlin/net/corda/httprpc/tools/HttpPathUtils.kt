package net.corda.httprpc.tools

import net.corda.utilities.trace
import org.slf4j.LoggerFactory

object HttpPathUtils {

    private val log = LoggerFactory.getLogger(HttpPathUtils::class.java)

    fun joinResourceAndEndpointPaths(resourcePath: String, endPointPath: String?): String {
        log.trace { "Map resourcePath: \"$resourcePath\" and endPointPath: \"$endPointPath\"" }
        val endPointPart = if (endPointPath == null) "" else "/$endPointPath"
        val repeatedSlashesRemoved = "$resourcePath$endPointPart".replace("/+".toRegex(), "/")
        val trailingSlashesRemoved = repeatedSlashesRemoved.replace("/+$".toRegex(), "")
        return trailingSlashesRemoved
    }

    /**
     * OpenAPI Path always starts with "/"
     */
    fun String.toOpenApiPath(): String =
        if (this.startsWith("/")) this
        else "/$this"
}