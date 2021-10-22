package net.corda.libs.configuration

import com.typesafe.config.ConfigObject

interface SmartConfigObject: ConfigObject {
    /**
     * Returns an instance of [SmartConfigObject] that never reveals secrets.
     */
    fun toSafeConfig(): SmartConfigObject
}

