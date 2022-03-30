package net.corda.customserializer.a;

public class NeedsCustomSerializerExampleA {

    private final int b;

    public NeedsCustomSerializerExampleA(int a) {
        this.b = a;
    }

    public int getB() {
        return b;
    }

    @Override
    public String toString() {
        return String.format("NeedsCustomSerializerExampleA{b=%d}", b);
    }
}
