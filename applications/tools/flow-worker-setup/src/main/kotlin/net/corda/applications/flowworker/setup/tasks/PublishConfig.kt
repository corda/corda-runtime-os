package net.corda.applications.flowworker.setup.tasks

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.data.config.Configuration
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC

class PublishConfig(private val context: TaskContext) : Task {

    override fun execute() {

        // Load the configuration file specified on the command line.
        val configurationFile = context.startArgs.configurationFile
        val configFileData = configurationFile!!.readText()
        val configFileConfig = ConfigFactory.parseString(configFileData)

        /* Publish each package.component section of the con+fig file
         * expected format:
         * corda {
         *     package1 {
         *         component1 {....}
         *         component2 {....}
         *     }
         *     package2 {
         *         component1 {....}
         *         component2 {....}
         *     }
         * }
         */

        for (componentKey in configFileConfig.getConfig("corda").root().keys) {
            val componentPath = "corda.${componentKey}"
            val configAsJson = configFileConfig.getConfig(componentPath).root().render(ConfigRenderOptions.concise())
            val content = Configuration(configAsJson, "5.0")
            val record = Record(CONFIG_TOPIC, componentPath, content)

            context.publish(record)
        }
    }
}