package net.corda.e2etest.utilities.config

interface TestConfigManager {
    /**
     * Loads a new configuration into the [TestConfigManager]. Configs are not applied to a cluster until [apply]
     * is called, so [load] only stages changes.
     *
     * `load("corda.flow", {"foo": "bar"})`
     * `load("corda.flow", {"foo": {"bar": {"hey": "ho"}}})`
     * `load("corda.flow", {"foo.bar.hey": "ho"})`
     *
     * @param section The section of config to update e.g. `corda.flow`
     * @param props Map of config properties to load. This can be single level (i.e. key-value), property trees
     *  (i.e. key-map), or property trees through key usage (i.e. using dot notation to reference lower values).
     */
    fun load(section: String, props: Map<String, Any?>): TestConfigManager

    /**
     * Loads a new configuration into the [TestConfigManager]. Configs are not applied to a cluster until [apply]
     * is called, so [load] only stages changes.
     *
     * `load("corda.flow", "foo", "bar")`
     * `load("corda.flow", "foo", {"bar": {"hey": "ho"}})`
     * `load("corda.flow", "foo.bar.hey", "ho")`
     *
     * @param section The section of config to update e.g. `corda.flow`
     * @param prop Config property name. Can also be a path to a config property in a config tree.
     * @param value Property value. Can be a map of values also.
     */
    fun load(section: String, prop: String, value: Any?): TestConfigManager

    /**
     * Using the cluster REST API, push all currently loaded configurations and wait for the new config to become
     * visible. Then run block, revert the configuration change and return the result of block.
     */
    fun <T> apply(block: () -> T): T

    fun <T> applyWithoutRevert(block: () -> T): T
}
