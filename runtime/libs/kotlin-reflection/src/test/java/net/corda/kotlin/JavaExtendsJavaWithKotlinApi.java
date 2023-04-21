package net.corda.kotlin;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@SuppressWarnings("unused")
public class JavaExtendsJavaWithKotlinApi extends AbstractJavaWithKotlinApi {
    @Override
    public void greet(@NotNull String name) {
    }

    @Override
    public void shareApi(@NotNull Object item) {
    }

    @NotNull
    @Override
    public String getShareApiProp() {
        return "GetShareApiProp";
    }

    @Override
    public void setShareApiProp(@NotNull String shareApiProp) {
    }

    @NotNull
    @Override
    public String getApiShow(@NotNull Collection<?> $this$getApiShow) {
        return "GetApiShow";
    }

    @Override
    public void setApiShow(@NotNull Collection<?> $this$setApiShow, @NotNull String apiShow) {
    }

    @Override
    public boolean testFunc(@NotNull byte[] $this$test, @NotNull Object obj) {
        return false;
    }

    @NotNull
    @Override
    public String anything() {
        return "Anything!";
    }

    @Override
    public void anything(int index) {
        System.out.println("Index: " + index);
    }
}
