package net.corda.cpi.read.impl.file.factory

import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPISegmentReader
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.read.impl.file.CPIReadImplFile
import net.corda.libs.configuration.SmartConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [CPIReadFactory::class], property = ["type=file"])
class CPIReadFactoryImpl @Activate constructor() : CPIReadFactory {
    override fun createCPIRead(nodeConfig: SmartConfig): CPIRead {
        return CPIReadImplFile(nodeConfig)
    }

    override fun createCPIReadSegment(
        nodeConfig: SmartConfig
    ): CPISegmentReader {
        return CPIReadImplFile(nodeConfig)
    }
}
