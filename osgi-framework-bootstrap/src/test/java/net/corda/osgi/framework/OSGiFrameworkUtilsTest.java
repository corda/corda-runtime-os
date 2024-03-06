package net.corda.osgi.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static net.corda.osgi.framework.OSGiFrameworkUtils.getFrameworkFrom;
import static net.corda.osgi.framework.OSGiFrameworkUtils.isActive;
import static net.corda.osgi.framework.OSGiFrameworkUtils.isStartable;
import static net.corda.osgi.framework.OSGiFrameworkUtils.isStoppable;
import static net.corda.osgi.framework.OSGiFrameworkUtils.removeTrailingComment;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSGiFrameworkUtilsTest {
    private Path frameworkStorageDir;

    @BeforeEach
    void setup(@TempDir Path frameworkStorageDir) {
        this.frameworkStorageDir = frameworkStorageDir;
    }

    @Test
    void getFrameworkFromReturnsFramework() throws Exception {
        final Logger logger = LoggerFactory.getLogger(OSGiFrameworkMain.class);
        final Framework framework = getFrameworkFrom(frameworkStorageDir, this.getClass().getClassLoader(), logger);
        assertNotNull(framework);
    }

    @Test
    void removeTrailingCommentRemovesComment() {
        assert (removeTrailingComment("here is a line #with a trailing comment")).equals("here is a line ");
    }

    @Test
    void isStoppableOnlyWhenStoppable() {
        assertTrue(isStoppable(Bundle.STOPPING));
        assertTrue(isStoppable(Bundle.ACTIVE));

        assertFalse(isStoppable(Bundle.STARTING));
        assertFalse(isStoppable(Bundle.UNINSTALLED));
        assertFalse(isStoppable(Bundle.INSTALLED));
        assertFalse(isStoppable(Bundle.RESOLVED));
    }

    @Test
    void isActiveOnlyWhenActive() {
        assertTrue(isActive(Bundle.ACTIVE));

        assertFalse(isActive(Bundle.STARTING));
        assertFalse(isActive(Bundle.STOPPING));
        assertFalse(isActive(Bundle.UNINSTALLED));
        assertFalse(isActive(Bundle.INSTALLED));
        assertFalse(isActive(Bundle.RESOLVED));
    }

    @Test
    void isStartableOnlyWhenStartable() {
        assertTrue(isStartable(Bundle.INSTALLED));
        assertTrue(isStartable(Bundle.RESOLVED));
        assertTrue(isStartable(Bundle.STARTING));

        assertFalse(isStartable(Bundle.ACTIVE));
        assertFalse(isStartable(Bundle.STOPPING));
        assertFalse(isStartable(Bundle.UNINSTALLED));
    }

}
