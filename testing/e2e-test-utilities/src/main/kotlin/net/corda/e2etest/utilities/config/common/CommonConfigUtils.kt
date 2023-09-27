package net.corda.e2etest.utilities.config.common

import net.corda.e2etest.utilities.config.TestConfigManager
import net.corda.schema.configuration.ConfigKeys

fun TestConfigManager.disableCertificateRevocationChecks() {
    load(ConfigKeys.P2P_GATEWAY_CONFIG, "sslConfig.revocationCheck.mode", "OFF")
}