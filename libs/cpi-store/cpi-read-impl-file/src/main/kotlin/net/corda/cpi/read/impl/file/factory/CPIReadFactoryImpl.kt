package net.corda.cpi.read.impl.file.factory

import com.typesafe.config.Config;
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPISegmentReader
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.read.impl.file.CPIReadImplFile
import net.corda.data.packaging.CPISegmentRequest
import net.corda.data.packaging.CPISegmentResponse
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [CPIReadFactory::class], property = ["type=file"])
class CPIReadFactoryImpl @Activate constructor() : CPIReadFactory {
    override fun createCPIRead(nodeConfig: Config): CPIRead {
        return CPIReadImplFile(nodeConfig)
    }

    // TODO: Do we need the below creation func?
    override fun createCPIReadSegment(
        nodeConfig: Config
    ): CPISegmentReader {
        return CPIReadImplFile(nodeConfig)
    }
}
