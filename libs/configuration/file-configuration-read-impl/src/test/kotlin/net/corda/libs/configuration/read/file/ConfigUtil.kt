package net.corda.libs.configuration.read.file

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl

object ConfigUtil {

    fun testConfigMap(): Map<String, SmartConfig> {
        val configMap = mutableMapOf<String, SmartConfig>()

        configMap["corda.database"] = databaseConfig(5.4)
        configMap["corda.security"] = securityConfig(5.4)

        return configMap
    }

    fun databaseConfig(version: Double): SmartConfig = SmartConfigImpl(ConfigFactory.parseString(
        """
            transactionIsolationLevel = READ_COMMITTED
            schema = corda
            runMigration=true
            componentVersion="$version"
        """.trimIndent()
    ))

    fun securityConfig(version: Double): SmartConfig = SmartConfigImpl(ConfigFactory.parseString(
        """
            authService {
                dataSource {
                    type = INMEMORY
                    users = [
                        {
                            username: "corda"
                            password: "corda"
                            permissions = [
                                ALL
                            ]
                        }
                        {
                            username = "archive"
                            password = "archive"
                            permissions = [
                                ALL
                            ]
                        }
                    ]
                }
            }
            componentVersion="$version"
        """.trimIndent()
    ))
}