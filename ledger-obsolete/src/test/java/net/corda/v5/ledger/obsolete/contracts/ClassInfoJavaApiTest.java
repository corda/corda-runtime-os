package net.corda.v5.ledger.obsolete.contracts;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClassInfoJavaApiTest {

    private final ClassInfo classInfo = new ClassInfo("bundleName", "bundleVersion", "classname");

    @Test
    public void bundleName() {
        Assertions.assertThat(classInfo.getBundleName()).isNotNull();
    }

    @Test
    public void bundleVersion() {
        Assertions.assertThat(classInfo.getBundleVersion()).isNotNull();
    }

    @Test
    public void classname() {
        Assertions.assertThat(classInfo.getClassname()).isNotNull();
    }
}
