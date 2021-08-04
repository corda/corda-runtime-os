package net.corda.data

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.uncheckedCast
import org.apache.avro.specific.SpecificRecordBase
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil

class AvroGeneratedMessageClasses {
    companion object {
        fun getAvroGeneratedMessageClasses(clazz: Class<*>): Set<Class<out SpecificRecordBase>> {
            val bundle: Bundle? = FrameworkUtil.getBundle(clazz)
            val resourceFile = "net/corda/data/generated-avro-message-classes.txt"
            val generatedClassList =
                bundle?.getResource(resourceFile)?.openStream()
                    ?: clazz.classLoader.getResourceAsStream(resourceFile)
                    ?: throw SchemaLoadException("Unable to load generated Avro message classes.")
            val classNames = generatedClassList.reader().readLines()
            val classes = classNames.map { clazz.classLoader.loadClass(it) }.toSet()
            return uncheckedCast(classes)
        }
    }
}

class SchemaLoadException(msg: String) : CordaRuntimeException(msg)