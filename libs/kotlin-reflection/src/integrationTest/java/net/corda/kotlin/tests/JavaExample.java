package net.corda.kotlin.tests;

import net.corda.kotlin.test.example.ExtendedKotlinApi;
import net.corda.kotlin.test.example.KotlinBase;
import net.corda.kotlin.test.example.SampleAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

@SampleAnnotation("Java Example")
public class JavaExample extends KotlinBase implements ExtendedKotlinApi {
    public static final String MESSAGE = "Hello from Java!";

    @Override
    public long getNativeLong() {
        return 0;
    }

    @Override
    @Nullable
    public String getNullableString() {
        return null;
    }

    @Override
    @NotNull
    public String getNonNullableString() {
        return MESSAGE;
    }

    @Override
    @NotNull
    public String getNeverNull() {
        return MESSAGE;
    }

    @Override
    @Nullable
    protected Object getProtectedBaseNullable() {
        return null;
    }

    @Override
    @NotNull
    public List<String> getListOfItems() {
        return emptyList();
    }

    @Override
    @NotNull
    public ArrayList<Object> anything() {
        return new ArrayList<>();
    }

    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private String privateVar = "Secret";
}
