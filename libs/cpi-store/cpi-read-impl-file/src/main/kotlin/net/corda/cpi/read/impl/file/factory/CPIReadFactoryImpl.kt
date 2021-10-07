package net.corda.cpi.read.impl.kafka.factory;

import com.typesafe.config.Config;
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.read.impl.file.CPIReadImplFile
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [CPIReadFactory::class])
class CPIReadFactoryImpl @Activate constructor() : CPIReadFactory {
    override fun createCPIRead(nodeConfig: Config): CPIRead {
        return CPIReadImplFile(nodeConfig)
    }
}
