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
import static net.corda.osgi.framework.OSGiFrameworkUtils.isBundleStartable;
import static net.corda.osgi.framework.OSGiFrameworkUtils.isBundleStoppable;
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
        final Logger logger = LoggerFactory.getLogger(OSGiFrameworkUtilsTest.class);
        final Framework framework = getFrameworkFrom(frameworkStorageDir, this.getClass().getClassLoader(), logger);
        assertNotNull(framework);
    }

    @Test
    void removeTrailingCommentRemovesComment() {
        assert (removeTrailingComment("here is a line #with a trailing comment")).equals("here is a line ");
    }

    @Test
    void isStoppableOnlyWhenStoppable() {
        assertTrue(isBundleStoppable(Bundle.STOPPING));
        assertTrue(isBundleStoppable(Bundle.ACTIVE));

        assertFalse(isBundleStoppable(Bundle.STARTING));
        assertFalse(isBundleStoppable(Bundle.UNINSTALLED));
        assertFalse(isBundleStoppable(Bundle.INSTALLED));
        assertFalse(isBundleStoppable(Bundle.RESOLVED));
    }

    @Test
    void isStartableOnlyWhenStartable() {
        assertTrue(isBundleStartable(Bundle.INSTALLED));
        assertTrue(isBundleStartable(Bundle.RESOLVED));
        assertTrue(isBundleStartable(Bundle.STARTING));

        assertFalse(isBundleStartable(Bundle.ACTIVE));
        assertFalse(isBundleStartable(Bundle.STOPPING));
        assertFalse(isBundleStartable(Bundle.UNINSTALLED));
    }

}
