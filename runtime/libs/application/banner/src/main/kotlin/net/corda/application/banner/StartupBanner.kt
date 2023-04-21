package net.corda.application.banner

/**
 * Interface for defining a product start-up banner
 */
interface StartupBanner {
    /**
     * Print the start-up banner to the console
     *
     * @param name of the component
     * @param version of the component
     */
    fun get(name: String, version: String): String
}

