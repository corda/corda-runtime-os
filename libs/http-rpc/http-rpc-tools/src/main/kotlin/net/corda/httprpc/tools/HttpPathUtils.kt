package net.corda.httprpc.tools

import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory

object HttpPathUtils {

    private val log = LoggerFactory.getLogger(HttpPathUtils::class.java)

    fun joinResourceAndEndpointPaths(resourcePath: String, endPointPath: String?): String {
        log.trace { "Map resourcePath: \"$resourcePath\" and endPointPath: \"$endPointPath\" to OpenApi path." }
        val endPointPart = if (endPointPath == null) "" else "/$endPointPath"
        val repeatedSlashesRemoved = "/$resourcePath$endPointPart".replace("/+".toRegex(), "/")
        val trailingSlashesRemoved = repeatedSlashesRemoved.replace("/+$".toRegex(), "")
        return trailingSlashesRemoved
            .also { log.trace { "Map resourcePath: \"$resourcePath\" and endPointPath: \"$endPointPath\" " +
                    "to OpenApi path: \"$it\" completed." } }
    }
}