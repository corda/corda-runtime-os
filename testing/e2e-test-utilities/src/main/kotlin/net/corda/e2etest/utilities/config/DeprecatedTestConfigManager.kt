package net.corda.e2etest.utilities.config

interface DeprecatedTestConfigManager: AutoCloseable {
    fun load(section: String, props: Map<String, Any?>): DeprecatedTestConfigManager
    fun load(section: String, prop: String, value: Any?): DeprecatedTestConfigManager
    fun apply(): DeprecatedTestConfigManager
    fun revert(): DeprecatedTestConfigManager
}
