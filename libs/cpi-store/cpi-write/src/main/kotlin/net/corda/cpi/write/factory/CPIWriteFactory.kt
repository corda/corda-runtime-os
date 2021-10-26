package net.corda.cpi.write.factory

import com.typesafe.config.Config
import net.corda.cpi.write.CPIWrite

interface CPIWriteFactory {
    fun createCPIWrite(nodeConfig: Config): CPIWrite
}