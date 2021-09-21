package net.corda.libs.configuration.read.file.factory

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.libs.configuration.read.file.ConfigRepository
import net.corda.libs.configuration.read.file.FileConfigReadServiceImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [ConfigReadServiceFactory::class])
class FileConfigReadServiceFactoryImpl @Activate constructor() : ConfigReadServiceFactory {

    override fun createReadService(bootstrapConfig: Config): ConfigReadService {
        return FileConfigReadServiceImpl(ConfigRepository(), bootstrapConfig)
    }
}
