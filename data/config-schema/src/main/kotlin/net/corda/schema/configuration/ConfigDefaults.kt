package net.corda.schema.configuration

/** Default configuration values for associated [ConfigKeys]
 *
 * NOTE: this is a temporary place for them.
 * Decision to be made on how to specify default values.
 * For example.
 *   - Constants
 *   - HCON config to use as fallback
 *   - JSON Schema
 *
 **/
object ConfigDefaults {
    val WORKSPACE_DIR = "${System.getProperty("java.io.tmpdir")}/corda/workspace"
    val TEMP_DIR = "${System.getProperty("java.io.tmpdir")}/corda/tmp"
}
