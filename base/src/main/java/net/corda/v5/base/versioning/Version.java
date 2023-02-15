package net.corda.v5.base.versioning;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of a version number
 */
public final class Version {
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

    private final int major;
    private final int minor;

    @SuppressWarnings("SameParameterValue")
    private static void requireNotNull(@Nullable Object obj, @NotNull String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Parses a version object from a string.
     *
     * @param versionString A string containing the version to be parsed.
     * @throws IllegalArgumentException An IllegalArgumentException is thrown if the string is not a well-formed
     * version string.
     */
    public static Version fromString(@NotNull String versionString) {
        requireNotNull(versionString, "versionString must not be null");
        final Matcher match = VERSION.matcher(versionString);
        if (match.matches()) {
            return new Version(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
        } else {
            throw new IllegalArgumentException(versionString + " is not a valid version string");
        }
    }

    /**
     * @param major The major part of the version
     * @param minor The minor part of the version
     */
    public Version(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Version)) {
            return false;
        }
        final Version other = (Version) obj;
        return major == other.major && minor == other.minor;
    }

    @Override
    public int hashCode() {
        return major * 31 ^ minor;
    }

    /**
     * Prints the version as a string in the form of <major>.<minor>.
     */
    @Override
    @NotNull
    public String toString() {
        return Integer.toString(major) + '.' + minor;
    }
}
