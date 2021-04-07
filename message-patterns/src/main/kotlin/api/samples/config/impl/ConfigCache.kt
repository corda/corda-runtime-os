package api.samples.config.impl


/**
 * Setters here could be called by a thread consuming from a kafka topic
 * This could sandboxed away/made private and only the ConfigService is exposed.
 * No Code in this repo to show a consumer thread pushing updates to the ConfigCache
 */
interface ConfigCache {

    fun getSomeValue() : String
    fun setSomeValue(string: String)

    fun getSomeOtherValue() : String
    fun setSomeOtherValue(string: String)

    fun getSomeOtherValues() : List<String>
    fun setSomeOtherValues(strings: List<String>)

    fun getSomeMoreValues() : List<Long>
    fun setSomeMoreValues(longs: List<Long>)

}