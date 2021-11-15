package irresolvablesandbox

import org.osgi.service.cm.ConfigurationAdmin

/** Forces a bundle import of the bundle containing [ConfigurationAdmin]. */
fun main() {
    println(ConfigurationAdmin::class.java)
}