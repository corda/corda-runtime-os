package net.corda.db.admin.impl

import net.corda.db.admin.DbChange
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLEncoder
import java.util.Collections.unmodifiableSet

/**
 * Classloader implementation of [DbChange]
 * This will provide ChangeLog files that are present in the classloader as resource files.
 *
 * @property list of [ChangeLogResourceFiles] that will be processed in order. Associated classloaders will be de-duped.
 * @constructor Create empty Classloader change log
 */
class ClassloaderChangeLog(
    private val changelogFiles: LinkedHashSet<ChangeLogResourceFiles>,
) : DbChange {
    /**
     * Definition of a master change log file and associated classloader
     * [name] is required so to be able to support "overlapping" resource files.
     * I.e. if 2 resource files with the same name exist in 2 different classloaders (e.g. migration/bob.xml in 2
     * different bundles), then a name must be specified and this name can be uses in the "file" attribute of an
     * "include" tag in a ChangeLog file.
     * Name could be the jar or bundle name, for example.
     *
     * @property name that uniquely identifies the set of changelogs related to the classloader
     * @property masterFiles one or more master ChangeLog files in order the should be executed.
     * @property classLoader defaulted to current classloader
     */
    data class ChangeLogResourceFiles(
        val name: String,
        val masterFiles: List<String>,
        val classLoader: ClassLoader = ChangeLogResourceFiles::class.java.classLoader
    ) {
        init {
            if (masterFiles.isEmpty())
                throw IllegalArgumentException("masterFiles must have at least one item.")
        }
    }

    companion object {
        const val CLASS_LOADER_PREFIX = "classloader://"
    }

    private val allChangeFiles = mutableSetOf<String>()

    private val distinctLoaders by lazy {
        changelogFiles.mapTo(HashSet(), ChangeLogResourceFiles::classLoader)
    }

    override val masterChangeLogFiles by lazy {
        changelogFiles.flatMap { clf ->
            clf.masterFiles.map { mf ->
                "$CLASS_LOADER_PREFIX${URLEncoder.encode(clf.name, "utf-8")}/${mf}"
            }
        }.distinct()
    }

    override val changeLogFileList: Set<String>
        get() {
            return unmodifiableSet(allChangeFiles)
        }


    private fun fetchClassLoader(path: String): InputStream? {
        val splitPath = path.removePrefix(CLASS_LOADER_PREFIX).split('/', limit = 2)
        if (splitPath.size != 2 || splitPath[1].isEmpty())
            throw IllegalArgumentException("$path is not a valid classloader resource path.")
        val cl = changelogFiles.firstOrNull { URLEncoder.encode(it.name, "utf-8") == splitPath[0] }
            ?: throw IllegalArgumentException("Cannot find classloader ${splitPath[0]} from $path")
        allChangeFiles.add(path)
        return cl.classLoader.getResourceAsStream(splitPath[1])
    }

    private fun fetchAllClassLoaders(path: String, relativeTo: String?): InputStream? {
        distinctLoaders.forEach {
            val resource = it.getResourceAsStream(path)
            if (null != resource) {
                allChangeFiles.add(path)
                return resource
            }
            if (relativeTo != null) {
                val segments = relativeTo.split('/')
                val base = segments.subList(0, segments.size - 1).joinToString("/")
                val r2 = it.getResourceAsStream(base + "/" + path)
                if (null != r2) {
                    allChangeFiles.add(path)
                    return r2
                }
            }
        }
        return null
    }

    private fun fetchAllClassLoadersFlat(name: String): InputStream? {
        val migrationPath = "migration/"+name
        distinctLoaders.forEach {
            val resource = it.getResourceAsStream(migrationPath)
            if (null != resource) {
                allChangeFiles.add(migrationPath)
                return resource
            }
        }
        return null
    }

    override fun fetch(path: String, relativeTo:String?): InputStream {
        // if classloader is specified
        if (path.startsWith(CLASS_LOADER_PREFIX)) {
            val r = fetchClassLoader(path)
            if (null != r) return r
        }
        val r2 =  fetchAllClassLoaders(path, relativeTo)
        if (r2 != null) return r2

        val r3 = fetchAllClassLoadersFlat(path.split('/').last())
        if (r3 != null) return r3

        throw FileNotFoundException("$path not found with relative path $relativeTo")
    }
}
