package net.corda.libs.configuration.read.file.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.read.ConfigReader
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.libs.configuration.read.file.ConfigRepository
import net.corda.libs.configuration.read.file.FileConfigReaderImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [ConfigReaderFactory::class])
class FileConfigReaderFactoryImpl @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
) : ConfigReaderFactory {

    override fun createReader(bootstrapConfig: SmartConfig): ConfigReader {
        return FileConfigReaderImpl(ConfigRepository(), bootstrapConfig, smartConfigFactory)
    }
}
