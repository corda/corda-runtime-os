package net.corda.flow.fiber

import org.slf4j.LoggerFactory
import kotlin.concurrent.thread


/**
 * Log error message and terminate the process. This might not clean up resources and could leave
 * the system in a messy state.
 */

//TODO: Review JIRA:
@Suppress("ForbiddenComment")
@Synchronized
fun errorAndTerminate(message: String, e: Throwable?) {
    try {
        thread {
            val log = LoggerFactory.getLogger("errorAndTerminate")
            log.error(message, e)
        }


        // give the logger a chance to flush the error message before killing the node
        Thread.sleep(10000L)
    } finally {
        Runtime.getRuntime().halt(1)
    }
}