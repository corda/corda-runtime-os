package net.corda.cpi.read.factory

import com.typesafe.config.Config
import net.corda.cpi.read.CPIRead

interface CPIReadFactory {
    fun createCPIRead(nodeConfig: Config): CPIRead
}