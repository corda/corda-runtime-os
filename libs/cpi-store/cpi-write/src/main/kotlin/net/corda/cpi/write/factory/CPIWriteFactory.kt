package net.corda.cpi.write.factory

import net.corda.cpi.write.CPIWrite
import net.corda.libs.configuration.SmartConfig

interface CPIWriteFactory {
    fun createCPIWrite(nodeConfig: SmartConfig): CPIWrite
}