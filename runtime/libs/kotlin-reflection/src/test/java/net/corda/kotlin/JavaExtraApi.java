package net.corda.kotlin;

@SuppressWarnings("unused")
public interface JavaExtraApi extends JavaApi {
    String EXTRA_API_CONST = "Extra-Api-Field";

    @Override
    void setValue(String data);

    int run();
}
