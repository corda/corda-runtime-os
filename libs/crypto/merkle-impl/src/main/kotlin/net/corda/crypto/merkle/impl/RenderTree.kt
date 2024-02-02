package net.corda.crypto.merkle.impl

/**
 * Render a left-balanced Merkle tree of a certain size.
 *
 * This is a top level function not a method of `MerkleTreeImpl` since it is a pure function which
 * intentionally has no association with a specific instance of a `MerkleTreeImpl`. It is used by the
 * `MerkleTreeImpl.render` and `MerkleProofImpl.render` methods.
 *
 * @param leafLabels a label for each leaf.
 * @param nodeLabels optional labels for each node.
 * @return a string representation of the tree, over multiple lines, with unicode box characters
 */
fun renderTree(leafLabels: List<String>, nodeLabels: Map<Pair<Int, Int>, String> = emptyMap()): String {
    val treeSize = leafLabels.size
    val levels: MutableList<List<Pair<Int, Int>>> = makeLevels(treeSize)

    // Make a grid of box characters to show the tree structure.
    //
    // Originally I wanted to write this tree rendering as pure code
    // but that's really fiddly and I think this way is easier to maintain.
    val grid: MutableMap<Pair<Int, Int>, Char> = mutableMapOf()

    // First draw the horizontal line structure of the tree. These lines will
    // mostly get overwritten later to add the vertical elements.
    levels.forEachIndexed { level, ranges ->
        ranges.forEach { range ->
            // we don't want ━ to the right of the leaves, since that looks like an extra node
            if (level != 0) {
                val x = levels.size - level - 1
                grid.put(x to range.first, '━')
            }
        }
    }

    // Now draw the vertical lines on to the grid, and make a note of where the nodes go
    levels.forEachIndexed { level, ranges ->
        ranges.forEach { range ->
            val x = levels.size - level - 1
            if (range.first != range.second) {
                val extent = if (level > 0) {
                    val nextLevel = levels[level - 1]
                    nextLevel.first { child -> range.second >= child.first && range.second <= child.second }.first
                } else range.second

                check(range.first <= extent)
                if (range.first != extent) {
                    val curtop = grid.getOrDefault(x to range.first, ' ')
                    // Draw the top of the vertical line
                    grid[x to range.first] = when (curtop) {
                        '━' -> '┳'
                        else -> '┃'
                    }
                    // Draw the middle part of the line, i.e. everything apart from first and last row
                    for (y in range.first + 1 until extent) {
                        grid[x to y] = '┃'
                    }
                    // Draw the bottom of the vertical line
                    grid[x to extent] = '┗'
                }
            }
        }
    }

    // Work out how much space to leave for node label columns.
    val longestLabels = (0 until levels.size).map { x ->
        (0 until treeSize).map { y ->
            (nodeLabels.get(x to y) ?: "").length
        }.max()
    }

    // Work out a map from (x,y) coordinates where nodes appear to their index at that level.
    val nodeYCoordinates: Map<Pair<Int, Int>, Int> = (0 until levels.size).flatMap { x->
        (0 until levels[x].size).map { rangeIndex ->
            (levels.size - x - 1 to levels[x][rangeIndex].first) to rangeIndex
        }
    }.toMap()

    // Work out the lines as strings, using the box characters, node and leaf labels.
    val lines: List<String> = (0 until treeSize).map { y ->
        val line = (0 until levels.size).map { x ->
            // x is the level of the tree, so x=0 is the top node
            // work out this node's index at the current level of the tree we are at, or -1 if we aren't at a node
            val nodeIndex = nodeYCoordinates.get(x to y)?:-1
            "${nodeLabels[x to nodeIndex]?.padEnd(longestLabels[x], ' ')?:(" ".repeat(longestLabels[x]))}${grid.getOrDefault(x to y, ' ')}"
        }
        val label: String = leafLabels.getOrNull(y) ?: ""
        "${line.joinToString("")}$label"
    }

    return lines.joinToString("\n")     // Return the whole tree as a single string.
}

/**
 * Produce a list of the ranges covered by each node.
 *
 * The first entry will have a list with one element per leaf, e.g. listOf( 0 to 0, 1 to 1, 2 to 2 ...)
 * The second entry will have the first level of nodes, e.g. listOf(0 to 1, 2 to 3, 4 to 5,... )
 * The last entry entry will be a single item list of the root, with the first element of the pair being 0 and the last being the tree size.
 *
 * @param treeSize The number of leaves in the tree.
 * @return list of ranges at a given level
 */
fun makeLevels(treeSize: Int): MutableList<List<Pair<Int, Int>>> {
    // Work out the tree structure, using iteration.
    // Loop variable is `values`: the leaf range values at the current level, starting at the bottom where each
    // leaf range will be a single leaf. As we loop around the leafs get rolled up, until we have a single root node
    // covering all the leaves.
    var values: MutableList<Pair<Int, Int>> = (0 until treeSize).map { it to it }.toMutableList()
    val levels: MutableList<List<Pair<Int, Int>>> = mutableListOf(values.toList())
    while (values.size > 1) {
        val newValues: MutableList<Pair<Int, Int>> = mutableListOf()
        var index = 0 // index into node hashes, which starts off with an entry per leaf
        while (index < values.size) {
            if (index < values.size - 1) {
                // pair the elements
                newValues += Pair(values[index].first, values[index + 1].second)
                index += 2
            } else {
                // promote the odd man out
                newValues += values[index]
                index++
            }
        }
        levels += newValues.toList()
        check(newValues.size < values.size)
        values = newValues
    }

    // We are left with a single root node which covers all the leaves.
    check(values.size == 1)
    check(values[0] == 0 to treeSize - 1)
    return levels
}