package net.corda.p2p.app.simulator

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import java.lang.NumberFormatException
import java.time.Duration
import java.time.format.DateTimeParseException

class ArgParsingUtils {
    companion object {

        class InvalidArgumentException(override val message: String) : Exception()

        /***
         * Get a database parameter from the command line or the config file. Command line options override options in
         * the config file.
         */
        fun getDbParameter(path: String, configFromFile: Config, parameters: CliParameters): String? {
            return getParameter(
                path,
                configFromFile.getConfigOrEmpty(AppSimulator.DB_PARAMS_PREFIX),
                parameters.databaseParams,
            )
        }

        /***
         * Get a topic creation parameter from the command line or the config file. Command line options override options in
         * the config file.
         */
        fun getTopicCreationParameter(path: String, default: Int, configFromFile: Config, parameters: CliParameters): Int {
            val stringParameter = getParameter(
                path,
                configFromFile.getConfigOrEmpty(AppSimulator.TOPIC_CREATION_PREFIX),
                parameters.topicCreationParams,
            ) ?: return default
            return try {
                Integer.parseInt(stringParameter)
            } catch (exception: NumberFormatException) {
                throw InvalidArgumentException("Topic creation parameter $path = $stringParameter is not an integer.")
            }
        }

        /***
         * Get a load generation parameter from the command line or the config file. Command line options override options in
         * the config file.
         */
        fun getLoadGenStrParameter(path: String, configFromFile: Config, parameters: CliParameters): String {
            return getParameter(
                path,
                configFromFile.getConfigOrEmpty(AppSimulator.LOAD_GEN_PARAMS_PREFIX),
                parameters.loadGenerationParams,
            ) ?: throw InvalidArgumentException(
                "Load generation parameter $path must be specified on the command line, using -l$path, " +
                    "or in a config file.",
            )
        }

        inline fun <reified E : Enum<E>> getLoadGenEnumParameter(path: String, configFromFile: Config, parameters: CliParameters): E {
            val stringParameter = getParameter(
                path,
                configFromFile.getConfigOrEmpty(AppSimulator.LOAD_GEN_PARAMS_PREFIX),
                parameters.loadGenerationParams,
            ) ?: throw InvalidArgumentException(
                "Load generation parameter $path must be specified on the command line, using -l$path," +
                    " or in a config file. Must be one of (${E::class.java.enumConstants.map { it.name }.toSet()}).",
            )
            return E::class.java.enumConstants.singleOrNull {
                it.name == stringParameter
            } ?: throw InvalidArgumentException(
                "Load generation parameter $path = $stringParameter is not one of " +
                    "(${E::class.java.enumConstants.map { it.name }.toSet()}).",
            )
        }

        fun getLoadGenIntParameter(path: String, default: Int, configFromFile: Config, parameters: CliParameters): Int {
            val stringParameter = getParameter(
                path,
                configFromFile.getConfigOrEmpty(AppSimulator.LOAD_GEN_PARAMS_PREFIX),
                parameters.loadGenerationParams,
            ) ?: return default
            return try {
                Integer.parseInt(stringParameter)
            } catch (exception: NumberFormatException) {
                throw InvalidArgumentException("Load generation parameter $path = $stringParameter is not an integer.")
            }
        }

        fun getLoadGenDuration(path: String, default: Duration, configFromFile: Config, parameters: CliParameters): Duration {
            val parameterFromCommandLine = parameters.loadGenerationParams[path]
            return if (parameterFromCommandLine == null) {
                try {
                    configFromFile.getDuration(path)
                } catch (exception: ConfigException.Missing) {
                    default
                }
            } else {
                try {
                    Duration.parse(parameterFromCommandLine)
                } catch (exception: DateTimeParseException) {
                    throw InvalidArgumentException(
                        "Load generation parameter $path = $parameterFromCommandLine can not be parsed as a " +
                            "duration. It must have the ISO-8601 duration format " +
                            "PnDTnHnMn.nS. e.g. PT20.5S gets converted to 20.5 seconds.",
                    )
                }
            }
        }

        fun getLoadGenDurationOrNull(path: String, configFromFile: Config, parameters: CliParameters): Duration? {
            val parameterFromCommandLine = parameters.loadGenerationParams[path]
            return if (parameterFromCommandLine == null) {
                try {
                    configFromFile.getDuration(path)
                } catch (exception: ConfigException.Missing) {
                    null
                }
            } else {
                try {
                    Duration.parse(parameterFromCommandLine)
                } catch (exception: DateTimeParseException) {
                    throw InvalidArgumentException(
                        "Load generation parameter $path = $parameterFromCommandLine can not be parsed " +
                            "as a duration. It must have the ISO-8601 duration format PnDTnHnMn.nS. e.g. PT20.5S gets converted to 20.5 " +
                            "seconds.",
                    )
                }
            }
        }

        fun getParameter(path: String, config: Config, parameters: Map<String, String>): String? {
            return parameters[path] ?: config.getStringOrNull(path)
        }

        fun Config.getConfigOrEmpty(path: String): Config {
            return getOrNull(path, this::getConfig) ?: ConfigFactory.empty()
        }

        internal fun Config.getIntOrNull(path: String): Int? {
            return getOrNull(path, this::getInt)
        }

        internal inline fun <reified E : Enum<E>> Config.getEnumOrNull(path: String): E? {
            return try {
                this.getEnum(E::class.java, path)
            } catch (exception: ConfigException.Missing) {
                null
            }
        }

        private fun Config.getStringOrNull(path: String): String? {
            return getOrNull(path, this::getString)
        }

        private fun <E> getOrNull(path: String, getFun: (String) -> E): E? {
            return try {
                getFun(path)
            } catch (exception: ConfigException.Missing) {
                null
            }
        }
    }
}
