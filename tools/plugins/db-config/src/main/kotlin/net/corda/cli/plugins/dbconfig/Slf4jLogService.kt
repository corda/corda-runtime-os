package net.corda.cli.plugins.dbconfig

import liquibase.logging.core.AbstractLogService
import org.slf4j.LoggerFactory

class Slf4jLogService : AbstractLogService() {

    override fun getPriority() = 5
    override fun getLog(clazz: Class<*>?) = Slf4jLogger(LoggerFactory.getLogger(clazz))

}