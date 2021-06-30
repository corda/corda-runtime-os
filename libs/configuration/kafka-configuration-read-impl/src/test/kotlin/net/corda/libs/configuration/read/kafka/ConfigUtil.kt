package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object ConfigUtil {

    fun testConfigMap(): Map<String, Config> {
        val configMap = mutableMapOf<String, Config>()

        configMap["corda.database"] = databaseConfig(5.4)
        configMap["corda.security"] = securityConfig(5.4)

        return configMap
    }

    fun databaseConfig(version: Double): Config = ConfigFactory.parseString(
        """
            transactionIsolationLevel = READ_COMMITTED
            schema = corda
            runMigration=true
            componentVersion="$version"
        """.trimIndent()
    )

    fun securityConfig(version: Double): Config = ConfigFactory.parseString(
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
    )
}