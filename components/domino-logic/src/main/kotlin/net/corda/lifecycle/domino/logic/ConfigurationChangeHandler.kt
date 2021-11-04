package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.domino.logic.util.ResourcesHolder

abstract class ConfigurationChangeHandler<C>(
    val configurationReaderService: ConfigurationReadService,
    val key: String,
    val configFactory: (Config) -> C,
) {

    abstract fun applyNewConfiguration(newConfiguration: C, oldConfiguration: C?, resources: ResourcesHolder)
}
