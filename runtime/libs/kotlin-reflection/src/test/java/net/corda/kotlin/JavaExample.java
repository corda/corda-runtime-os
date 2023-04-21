package net.corda.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

@SuppressWarnings("unused")
public class JavaExample extends JavaBase implements ExtraApi {
    public static final String PUBLIC_STATIC = "Public Static";

    @NotNull
    public static Object getStaticData() {
        return new Object();
    }

    public String publicField = "Public Message";
    protected String protectedField = "Protected Message";
    @SuppressWarnings("FieldMayBeFinal")
    private String privateField = "Private Message";
    String packagePrivateField = "Package Private Message";

    @Override
    @NotNull
    public String getFirstVal() {
        return "First Message";
    }

    private String _firstVar = "Override First Var";

    @Override
    @NotNull
    public String getFirstVar() {
        return _firstVar;
    }

    @Override
    public void setFirstVar(@NotNull String firstVar) {
        _firstVar = firstVar;
    }

    @Override
    @Nullable
    public String getSecondVal() {
        return null;
    }

    @Nullable
    @Override
    public String getSecondVar() {
        return null;
    }

    @Override
    public void setSecondVar(@Nullable String secondVar) {
    }

    @Nullable
    @Override
    public Integer getPrimitiveIntVal() {
        return 101;
    }

    @NotNull
    protected String getProtected() {
        return "Protected";
    }

    @NotNull
    String getPackagePrivate() {
        return "Package-Private";
    }

    @NotNull
    private String getPrivate() {
        return "Private";
    }

    @Override
    public void greet(@NotNull String name) {
        System.out.println("Hello, " + name);
    }

    @Override
    @NotNull
    public String tell(@NotNull String name, @Nullable String message) {
        return "Say '" + message + "' to " + name;
    }

    @Override
    public void shareApi(@NotNull Object item) {
        System.out.println("Enjoy " + item);
    }

    @Override
    @NotNull
    public ArrayList<Object> anything() {
        return new ArrayList<>();
    }

    @Override
    public void anything(int index) {
    }

    @NotNull
    @Override
    public Random anything(@NotNull String message, Object... params) {
        return new Random(message.hashCode());
    }

    @Override
    @NotNull
    public String getExtraApiExtensionProp(@NotNull String receiver) {
        return receiver;
    }

    @Override
    @Nullable
    public Object extraApiExtensionFunc(long receiver, @Nullable Object data) {
        return data;
    }

    private String sharePropField;

    @Override
    @NotNull
    public String getShareApiProp() {
        return sharePropField;
    }

    @Override
    public void setShareApiProp(@NotNull String shareProp) {
        sharePropField = shareProp;
    }

    @NotNull
    @Override
    public String getApiShow(@NotNull Collection<?> receiver) {
        return receiver.toString();
    }

    @Override
    public void setApiShow(@NotNull Collection<?> receiver, @NotNull String apiShow) {
    }

    @Override
    public boolean testFunc(@NotNull byte[] receiver, @NotNull Object obj) {
        return false;
    }
}
