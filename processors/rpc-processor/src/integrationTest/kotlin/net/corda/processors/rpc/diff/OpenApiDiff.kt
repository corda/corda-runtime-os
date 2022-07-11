package net.corda.processors.rpc.diff

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.tags.Tag

internal fun OpenAPI.diff(baseline: OpenAPI): List<String> {
    return info.diff(baseline.info) +
            paths.diff(baseline.paths) +
            tags.diff(baseline.tags) +
            components.diff(baseline.components)
}

private fun Info.diff(baseline: Info): List<String> {
    return if (this != baseline) {
        listOf("Info is different, baseline: $baseline, current: $this")
    } else emptyList()
}

private fun Paths.diff(baseline: Paths): List<String> {
    val differences = mutableListOf<String>()
    if (size != baseline.size) {
        differences.add("Different number of paths, baseline: ${baseline.size}, current: $size")
        val currentSet = this.keys
        val baselineSet = baseline.keys

        baselineSet.subtract(currentSet).forEach {
            differences.add("Path in baseline but not in current: $it")
        }

        currentSet.subtract(baselineSet).forEach {
            differences.add("Path in current but not in baseline: $it")
        }
    }

    entries.forEach { entry ->
        val baselineValue: PathItem? = baseline[entry.key]
        if(baselineValue == null) {
            differences.add("Path absent in baseline: Current path: ${entry.key}")
        } else {
            if (entry.value != baselineValue) {
                differences.add("Path different at [${entry.key}]. Current path: ${entry.value} is different to baseline: $baselineValue")
            }
        }
    }

    return differences
}

private fun List<Tag>.diff(baseline: List<Tag>): List<String> {
    val differences = mutableListOf<String>()
    if (size != baseline.size) {
        differences.add("Different number of tags, baseline: ${baseline.size}, current: $size")

        val currentSet = this.toSet()
        val baselineSet = baseline.toSet()

        baselineSet.subtract(currentSet).forEach {
            differences.add("Tag in baseline but not in current: $it")
        }

        currentSet.subtract(baselineSet).forEach {
            differences.add("Tag in current but not in baseline: $it")
        }
    }

    val currentSorted = this.sortedBy { it.name }
    val baselineSorted = baseline.sortedBy { it.name }
    (currentSorted.indices).forEach { i ->
        if (currentSorted[i] != baselineSorted[i]) {
            differences.add("Tags do not match. Current tag: ${currentSorted[i]} is different to baseline: ${baselineSorted[i]}")
        }
    }

    return differences
}

private fun Components.diff(baseline: Components): List<String> {
    return schemas.diff(baseline.schemas)
}

private fun Map<String, Schema<Any>>.diff(baseline: Map<String, Schema<Any>>): List<String> {
    val differences = mutableListOf<String>()
    if (size != baseline.size) {
        differences.add("Different number of schemas, baseline: ${baseline.size}, current: $size")
        val currentSet = this.keys
        val baselineSet = baseline.keys

        baselineSet.subtract(currentSet).forEach {
            differences.add("Schema in baseline but not in current: $it")
        }

        currentSet.subtract(baselineSet).forEach {
            differences.add("Schema in current but not in baseline: $it")
        }
    }

    entries.forEach { entry ->
        val baselineValue: Schema<Any>? = baseline[entry.key]
        if (baselineValue == null) {
            differences.add("Schema absent in baseline: Current schema: ${entry.key}")
        } else {
            differences += entry.value.diff(baselineValue, entry.key)
        }
    }
    return differences
}

private fun Schema<Any>.diff(baseline: Schema<Any>, key: String): List<String> {

    val differences = mutableListOf<String>()
    // We cannot use `equals()` as currently there is a bug in the way we produce `nullable` value for schema
    if (required != baseline.required) {
        differences.add("Schema differences for: $key. Current 'required': $required, baseline: ${baseline.required}")
    }

    if (type != baseline.type) {
        differences.add("Schema differences for: $key. Current 'type': $type, baseline: ${baseline.type}")
    }

    if (properties != baseline.properties) {
        differences.add("Schema differences for: $key. Current 'properties': $properties, baseline: ${baseline.properties}")
    }

    if (nullable != baseline.nullable) {
        differences.add("Schema differences for: $key. Current 'nullable': $nullable, baseline: ${baseline.nullable}")
    }

    return differences
}
