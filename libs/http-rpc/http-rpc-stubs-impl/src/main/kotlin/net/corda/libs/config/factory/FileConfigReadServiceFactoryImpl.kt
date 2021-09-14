package net.corda.libs.config.factory

import net.corda.libs.config.FileConfigReadService
import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [ConfigReadServiceFactory::class])
class FileConfigReadServiceFactoryImpl: ConfigReadServiceFactory {
    override fun createReadService(bootstrapConfig: Config): ConfigReadService {
        return FileConfigReadService()
    }
}