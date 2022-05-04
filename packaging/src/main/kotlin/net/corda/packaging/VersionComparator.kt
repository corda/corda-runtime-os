package net.corda.packaging

import kotlin.math.min

class VersionComparator : Comparator<String?> {
    data class Parts(val epoch: String, val version: String, val release: String, val hasRelease: Boolean)

    override fun compare(v1: String?, v2: String?): Int {
        return cmp(v1, v2)
    }

    companion object {
        private const val EPOCH_DELIMITER = ':'
        private const val RELEASE_DELIMITER = '-'
        const val DEFAULT_EPOCH = "0"

        fun String.toVersionParts(): Parts {
            var epoch = DEFAULT_EPOCH
            var version: String
            var release = ""
            var epochTerminator = 0

            // epoch is a leading *number* prefix, terminating with ':'
            while (epochTerminator < this.length && Character.isDigit(this[epochTerminator])) epochTerminator++
            val hasEpochValue = epochTerminator < this.length && this[epochTerminator] == EPOCH_DELIMITER
            if (hasEpochValue) {
                version = this.substring(epochTerminator + 1)
                epoch = this.substring(0, epochTerminator)
                if (epoch.isEmpty()) epoch = DEFAULT_EPOCH
            } else {
                version = this
            }

            val versionEndIndex = version.indexOf(RELEASE_DELIMITER)

            // We seem to want this for 'old' behaviour.  Delete 'hasRelease'
            // and test 'release.isEmpty()' if we want better behaviour?
            val hasRelease: Boolean
            if (versionEndIndex != -1) {
                release = version.substring(versionEndIndex + 1)
                version = version.substring(0, versionEndIndex)
                hasRelease = true
            } else {
                hasRelease = false
            }

            return Parts(epoch, version, release, hasRelease)
        }

        /**
         * Compare a section of a version string, i.e.
         *
         * ```
         * [epoch]:[version]-[release]
         * ```
         *
         * where `epoch` is a number, `version` is 'anything' and `release` is 'anything'
         *
         * We don't expect to ever use 'epoch'.
         */
        @Suppress("ComplexMethod")
        private fun comparePartOfVersionString(part1: String, part2: String): Int {
            // Fast comparison.
            if (part1 == part2) return 0

            // We're now going to gradually remove the front of the strings in
            // each loop iteration until we're done.  As written, delimiters can
            // be anything, i.e. "1.2.3" or even "1!2!3" (which is weird).
            // The loop will process:  1.2.3, .2.3, .3

            var remaining1 = part1
            var remaining2 = part2

            // loop through each version segment of str1 and str2 and compare them
            while (remaining1.isNotEmpty() && remaining2.isNotEmpty()) {
                // prune separators
                var idx1 = skipNonAlphaNumeric(remaining1)
                var idx2 = skipNonAlphaNumeric(remaining2)
                remaining1 = remaining1.substring(idx1)
                remaining2 = remaining2.substring(idx2)

                // If we ran to the end of either, we are finished with the loop
                if (remaining1.isEmpty() || remaining2.isEmpty()) break

                // If the separator lengths were different, we are also finished
                if (idx1 != idx2) return if (idx1 < idx2) -1 else 1

                // grab first completely alpha or completely numeric segment
                // leave one and two pointing to the start of the alpha or numeric
                // segment and walk ptr1 and ptr2 to end of segment

                val (segment1IsANumber, endIdx1, endIdx2) = skipDigitsOrLetters(remaining1, remaining2)

                // assert(endIdx1 != 0, "shouldn't happen")
                // we've skipped non-alphanumeric to the first alphanumeric, so
                // endIdx1 must be at least 1

                var segment1 = remaining1.substring(0, endIdx1)
                var segment2 = remaining2.substring(0, endIdx2)

                // When the two version segments are different types: one numeric,
                // the other alpha (i.e. empty) numeric segments are always
                // lexicographically 'newer' than alpha segments
                if (segment2.isEmpty()) {
                    return if (segment1IsANumber) 1 else -1
                }

                if (segment1IsANumber) {
                    // chomp leading zeroes - this means 009 == 9
                    idx1 = skipLeadingZeroes(segment1)
                    idx2 = skipLeadingZeroes(segment2)

                    segment1 = segment1.substring(idx1)
                    segment2 = segment2.substring(idx2)

                    // return early if more digits
                    if (segment1.length > segment2.length) {
                        return 1
                    } else if (segment2.length > segment1.length) {
                        return -1
                    }
                }

                val rc = segment1.clampedCompareTo(segment2)

                // If they're the same we might have more substrings to compare.
                if (rc != 0) return rc

                // shrink the remaining string to process
                remaining1 = remaining1.substring(min(endIdx1, remaining1.length))
                remaining2 = remaining2.substring(min(endIdx2, remaining2.length))
            }

            // this catches the case where all numeric and alpha segments have
            // compared identically but the segment separating characters were
            // different
            if (remaining1.isEmpty() && remaining2.isEmpty()) {
                return 0
            }

            // We never want a remaining alpha string to
            // beat an empty string.  The logic is:
            // - if one is empty and two is not an alpha, two is newer.
            // - if one is an alpha, two is newer.
            // - otherwise one is newer.
            return if (remaining1.isEmpty() && !Character.isAlphabetic(remaining2.first().code)) {
                -1
            } else if (remaining1.isNotEmpty() && Character.isAlphabetic(remaining1.first().code)) {
                -1
            } else {
                1
            }
        }

        //<editor-fold desc="skip functions">
        /**
         * Skip digits or letters in a string, and return the indexes
         * of where we've skipped to, and also whether `str1` is a number.
         *
         * We specifically test `str1` for to see if it is a number
         *
         * @return
         *
         *  boolean str1 is a number,
         *
         *  index we've skipped to for str1,
         *
         *  index we've skipped to for str2
         *
         */
        private fun skipDigitsOrLetters(
            str1: String,
            str2: String
        ): Triple<Boolean, Int, Int> {
            var pos1 = 0
            var pos2 = 0
            val isNum: Boolean
            if (Character.isDigit(str1[pos1])) {
                pos1 = skipDigits(str1, pos1)
                pos2 = skipDigits(str2, pos2)
                isNum = true
            } else {
                pos1 = skipLetters(str1, pos1)
                pos2 = skipLetters(str2, pos2)
                isNum = false
            }
            return Triple(isNum, pos1, pos2)
        }

        /**
         * return the first index of the string that's not char '0'
         */
        private fun skipLeadingZeroes(str: String): Int {
            var idx = 0
            while (idx < str.length && str[idx] == '0') idx++
            return idx
        }

        /**
         * Return first index that is not a letter, i.e. skip the letters in the
         * front of this string
         */
        private fun skipLetters(str: String, startIndex: Int): Int {
            if (str.isEmpty()) return startIndex
            var idx = startIndex
            while (idx < str.length) {
                if (!Character.isAlphabetic(str[idx].code)) break
                idx++
            }
            return idx
        }

        /**
         * Return first index that is not a digit, i.e. skip all the digits in
         * the front of this string
         */
        private fun skipDigits(str: String, startIndex: Int): Int {
            if (str.isEmpty()) return startIndex
            var idx = startIndex
            while (idx < str.length) {
                if (!Character.isDigit(str[idx])) break
                idx++
            }
            return idx
        }

        /**
         * Return the index of the next alphanumeric character
         */
        private fun skipNonAlphaNumeric(str: String): Int {
            var idx = 0
            while (idx < str.length) {
                val ch = str[idx]
                if (Character.isAlphabetic(ch.code) || Character.isDigit(ch)) break
                idx++
            }
            return idx
        }
        //</editor-fold>

        /**
         * [String.compareTo] returns negative, 0, or positive, and we need to clamp it to -1, 0, 1
         *
         * @return clamped lexicographical order, i.e. -1, 0, 1
         */
        private fun String.clampedCompareTo(other: String): Int {
            val compareTo = this.compareTo(other)
            return if (compareTo > 0) 1
            else if (compareTo < 0) -1
            else 0
        }

        @JvmStatic
        fun cmp(v1: String?, v2: String?): Int {
            // Simple early-exit comparisons.
            if (v1 == null && v2 == null) {
                return 0
            } else if (v1 == v2) {
                return 0
            } else if (v1 == null) {
                return -1
            } else if (v2 == null) {
                return 1
            } else if (v1.isEmpty()) {
                return -1
            } else if (v2.isEmpty()) {
                return 1
            }

            return complexComparison(v1, v2)
        }

        private fun complexComparison(v1: String, v2: String): Int {
            val parts1 = v1.toVersionParts()
            val parts2 = v2.toVersionParts()

            var ret = comparePartOfVersionString(parts1.epoch, parts2.epoch)
            if (ret != 0) return ret

            ret = comparePartOfVersionString(parts1.version, parts2.version)
            if (ret != 0) return ret

            // This is the 'old' behaviour - I'm not convinced it's correct, but this is what it does.
            // i.e. 1.2.3 and 1.2.3-abc are considered *the same*
            if (parts1.hasRelease && parts2.hasRelease) {
                ret = comparePartOfVersionString(parts1.release, parts2.release)
            }
            return ret
        }
    }
}
