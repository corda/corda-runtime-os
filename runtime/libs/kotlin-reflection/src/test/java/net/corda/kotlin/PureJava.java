package net.corda.kotlin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class PureJava implements JavaExtraApi {
    public static final String PUBLIC_STATIC_FINAL = "Public-Static-Final";
    public static String PUBLIC_STATIC = "Public-Static";

    protected static final String PROTECTED_STATIC_FINAL = "Protected-Static-Final";
    protected static String PROTECTED_STATIC = "Protetced-Static";

    private static final String PRIVATE_STATIC_FINAL = "Private-Static-Final";
    private static String PRIVATE_STATIC = "Private-Static";

    static final String PACKAGE_PRIVATE_STATIC_FINAL = "Package-Private-Static-Final";
    static String PACKAGE_PRIVATE_STATIC = "Package-Private-Static";

    public final String publicFinalFileld = "Public-Final";
    public String publicField = "Public";

    protected final String protectedFinalField = "Protected-Final";
    protected String protectedField = "Protected";

    private final String privateFinalField = "Private-Final";
    private String privateField = "Private";

    final String packagePrivateFinalField = "Package-Private-Final";
    String packagePrivateField = "Package-Private";

    public static void publicStaticFunc(String data) {
    }

    protected static void protectedStaticFunc(String data) {
    }

    static void packagePrivateStaticFunc(String data) {
    }

    private static void privateStaticFunc() {
    }

    boolean getPackagePrivateFlag() {
        return false;
    }

    protected long getProtectedLong() {
        return 0;
    }

    @Override
    public int getInteger() {
        return 0;
    }

    @Override
    public List<String> modify(List<String> inputs, String... values) {
        List<String> outputs = (inputs == null) ? new ArrayList<>() : inputs;
        Collections.addAll(outputs, values);
        return outputs;
    }

    @Override
    public void setValue(String data) {
    }

    @Override
    public int run() {
        return 0;
    }
}
