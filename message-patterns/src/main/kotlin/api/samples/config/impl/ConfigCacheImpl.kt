package api.samples.config.impl

import org.osgi.service.component.annotations.Component


@Component
class ConfigCacheImpl : ConfigCache {

    private var someValue :String = ""
    private var someOtherValue :String = ""
    private var someOtherValues = listOf<String>()
    private var someMoreOtherValues = listOf<Long>()

    override fun getSomeValue () : String {
        return someValue
    }

    override fun setSomeValue(string: String) {
        someValue = string
    }

    override fun getSomeOtherValue () : String {
        return someOtherValue
    }

    override fun setSomeOtherValue(string: String) {
        someOtherValue = string
    }

    override fun getSomeOtherValues () : List<String> {
        return someOtherValues
    }

    override fun setSomeOtherValues(strings: List<String>) {
        someOtherValues = strings
    }

    override fun getSomeMoreValues () : List<Long> {
        return someMoreOtherValues
    }

    override fun setSomeMoreValues(longs: List<Long>) {
        someMoreOtherValues = longs
    }
}