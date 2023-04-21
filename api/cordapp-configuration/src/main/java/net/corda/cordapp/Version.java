package net.corda.cordapp;

import org.gradle.api.tasks.StopExecutionException;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Version implements Comparable<Version> {
    private static final Pattern VERSION_PATTERN = Pattern.compile("((\\d++)(\\.\\d++)+)(-.+)?");

    private final int major;
    private final int minor;
    private final int revision;
    private final String tag;

    private Version(int major, int minor, int revision, String tag) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        this.tag = tag;
    }

    @Nonnull
    Version getBaseVersion() {
        return new Version(major, minor, revision, "");
    }

    @Override
    public int compareTo(@Nonnull Version version) {
        int result = major - version.major;
        if (result == 0) {
            result = minor - version.minor;
            if (result == 0) {
                result = revision - version.revision;
                if (result == 0) {
                    result = tag.compareTo(version.tag);
                }
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        } else if (object == null || object.getClass() != Version.class) {
            return false;
        }
        Version other = (Version) object;
        return major == other.major
                && minor == other.minor
                && revision == other.revision
                && tag.equals(other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, revision, tag);
    }

    @Override
    @Nonnull
    public String toString() {
        StringBuilder builder = new StringBuilder()
            .append(major).append('.').append(minor).append('.').append(revision);
        if (!tag.isEmpty()) {
            builder.append('-').append(tag);
        }
        return builder.toString();
    }

    @Nonnull
    static Version parse(String version) {
        final Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new StopExecutionException("Unsupported version format '" + version + '\'');
        }

        final String dottedVersion = matcher.group(1);
        final String majorVersion = matcher.group(2);
        final String revision = matcher.group(3);
        final String minorVersion = dottedVersion.substring(majorVersion.length(), dottedVersion.length() - revision.length());
        if (minorVersion.lastIndexOf('.') > 0) {
            // The '.' should either be the first character, or be missing entirely.
            throw new StopExecutionException("Unsupported version format '" + version + '\'');
        }
        return new Version(
            Integer.parseInt(majorVersion),
            parseComponent(minorVersion),
            parseComponent(revision),
            parseTag(matcher.group(4))
        );
    }

    private static int parseComponent(String component) {
        return isNullOrEmpty(component) ? 0 : Integer.parseInt(component.substring(1));
    }

    @Nonnull
    private static String parseTag(String str) {
        return isNullOrEmpty(str) ? "" : str.substring(1).toUpperCase();
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
