package net.corda.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public abstract class JavaBase extends Base {
    public static String publicStaticBaseField;
    protected static String protectedStaticBaseField;
    private static String privateStaticBaseField;
    static String packagePrivateStaticBaseField;

    @NotNull
    public static String publicStaticBaseFunc() {
        return "Public Static";
    }

    @NotNull
    protected static String protectedStaticBaseFunc() {
        return "Protected Static";
    }

    @NotNull
    private static String privateStaticBaseFunc() {
        return "Private Static";
    }

    @NotNull
    static String packagePrivateStaticBaseFunc() {
        return "Package Private Static";
    }

    @Override
    @Nullable
    public Object getBaseNullableVal() {
        return null;
    }

    @Override
    @Nullable
    protected Object getProtectedBaseNullableVal() {
        return null;
    }

    @Override
    @Nullable
    public String nullableBaseExtensionFunc(@NotNull Object baseObj, @Nullable String data) {
        return data == null ? null : baseObj + data;
    }

    @Override
    @NotNull
    public String nonNullableBaseExtensionFunc(@NotNull String baseObj, @NotNull String data) {
        return baseObj + data;
    }

    @Override
    @Nullable
    public String getNullableBaseExtensionProp(@NotNull Object baseObj) {
        return null;
    }

    @Override
    public void setNullableBaseExtensionProp(@NotNull Object baseObj, @Nullable String nullableBaseExtension) {
    }
}
