package net.corda.libs.configuration.read.file.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.read.ConfigReader
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.libs.configuration.read.file.ConfigRepository
import net.corda.libs.configuration.read.file.FileConfigReaderImpl
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [ConfigReaderFactory::class])
class FileConfigReaderFactoryImpl : ConfigReaderFactory {

    override fun createReader(bootstrapConfig: SmartConfig): ConfigReader {
        return FileConfigReaderImpl(ConfigRepository(), bootstrapConfig)
    }
}
