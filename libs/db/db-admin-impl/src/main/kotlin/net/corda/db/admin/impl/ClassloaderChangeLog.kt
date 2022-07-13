package net.corda.db.admin.impl

import net.corda.db.admin.DbChange
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLEncoder
import java.util.Collections.unmodifiableSet


fun normalizePath(path: String) = path.replace("//+".toRegex(), "/")

/**
 * Classloader implementation of [DbChange]
 * This allows ChangeLog files to be fetched that are present in the classloader as resource files.
 *
 * Keeps track and exposes the files that have been fetched, which is used by StreamResourceAccessor to
 * generate a "master" changelog file.
 *
 * As far as Liquibase changelog files are concerned, the absolute path to this changelog will be like:
 *
 *   classloader://foo/migration/test/fred.txt
 *
 *  Note: if liquibase tries to read the master changelog before fetching all the constituent changelog files then
 *  any which have not been fetched will not come through.
 *
 * @property list of [ChangeLogResourceFiles] that will be processed in order. Associated classloaders will be de-duped.
 * @constructor Create initially empty Classloader change log
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

    private fun fetchFromNominatedClassloader(path: String, relativeTo: String?): InputStream? {
        if (relativeTo != null) throw java.lang.IllegalArgumentException("Cannot combine classloader which is an absolute path $path and relativeTo path $relativeTo")
        val splitPath = normalizePath(path).removePrefix(normalizePath(CLASS_LOADER_PREFIX)).split('/', limit = 2)
        if (splitPath.size != 2 || splitPath[1].isEmpty())
            throw IllegalArgumentException("$path is not a valid classloader resource path.")
        val cl = changelogFiles.firstOrNull { URLEncoder.encode(it.name, "utf-8") == splitPath[0] }
            ?: throw IllegalArgumentException("Cannot find classloader ${splitPath[0]} from $path")
        allChangeFiles.add(path)
        return cl.classLoader.getResourceAsStream(splitPath[1])
    }

    // Search across all class loaders for path, perhaps looking relative to a specified file.
    // If leaf is set then strip out all directory elements from path and just look for the plain filename
    // in each classloader, which is our last ditch attempt to find something and is only used when
    // searching for the full path name fails.
    private fun fetchAllClassLoaders(path: String, relativeTo: String?, leaf: Boolean = false): InputStream? =
        distinctLoaders.firstNotNullOfOrNull {
            val pathElem = if (leaf) path.split('/').last() else path
            val usePath = if (relativeTo != null) {
                val segments = relativeTo.split('/')
                val base = segments.subList(0, segments.size - 1).joinToString("/")
                "$base/$pathElem"
            } else {
                pathElem
            }
            return it.getResourceAsStream(usePath)?.apply {
                allChangeFiles.add(usePath)
            }
        }

    // Lookup a path, possibly relative to another path.
    // If path begins with CLASS_LOADER_PREFIX look only in a nominated classloader otherwise
    // search every class loader we know.
    override fun fetch(path: String, relativeTo:String?): InputStream =
        (if (normalizePath(path).startsWith(normalizePath(CLASS_LOADER_PREFIX))) {
            fetchFromNominatedClassloader(path, relativeTo)
        } else {
            fetchAllClassLoaders(path, relativeTo)?:fetchAllClassLoaders(path, relativeTo, leaf=true)
        })?:throw FileNotFoundException("Changelog $path not found with relative path $relativeTo across ${distinctLoaders.size} class loaders")
}
