package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier

data class QualifiedType<T>(val rawParameterType: RawParameterType<T>, val qualifier: TypeQualifier) : ParameterType<T> {
    val expectedRawClass: Class<T> get() = rawParameterType.expectedType
    val expectedClass: Class<T> get() = typeLabel.expectedClass as Class<T>
    val isQualified: Boolean = true

    override fun getTypeLabel(): ParameterTypeLabel {
        return typeLabel
    }

    override fun getExpectedType(): Class<T> {
        return rawParameterType.expectedType
    }

    override fun isQualified(): Boolean {
        return isQualified
    }

    override fun getQualifier(): TypeQualifier {
        return qualifier
    }

    fun getExpectedRawClass(): Class<T> {
        return expectedRawClass
    }

    fun getExpectedClass(): Class<T> {
        return expectedClass
    }

    override fun toString(): String {
        return "$rawParameterType ($qualifier)"
    }
}


//package net.corda.v5.application.interop.parameters;
//
//import net.corda.v5.application.interop.facade.FacadeTypeQualifierInterface;
//import org.jetbrains.annotations.NotNull;
//
//public final class QualifiedType<T> implements ParameterType<T> {
//
//    @NotNull
//    RawParameterType<T> rawParameterType;
//
//    @NotNull
//    private final ParameterType<T> type;
//
//    @NotNull
//    private final FacadeTypeQualifierInterface qualifier;
//
//    @NotNull
//    private ParameterTypeLabel typeLabel;
//
//    @NotNull
//    private final Class<T> expectedClass = (Class<T>) typeLabel.getExpectedClass();
//
//    @NotNull
//    private final Class<T> expectedRawClass = (Class<T>) rawParameterType.getExpectedClass();
//
//    private final boolean isQualified = true;
//
//    public QualifiedType(RawParameterType<T> rawParameterType, @NotNull ParameterType<T> type, @NotNull FacadeTypeQualifierInterface qualifier, ParameterTypeLabel typeLabel) {
//        this.rawParameterType = rawParameterType;
//        this.type = type;
//        this.qualifier = qualifier;
//        this.typeLabel = typeLabel;
//    }
//
//    @NotNull
//    @Override
//    public Class<T> getExpectedType() {
//        return type.getExpectedType();
//    }
//
//    @Override
//    public String toString() {
//        return type + "(" + qualifier + ")";
//    }
//
//    public RawParameterType<T> getRawParameterType() {
//        return rawParameterType;
//    }
//
//    @NotNull
//    public ParameterType<T> getType() {
//        return type;
//    }
//
//    @NotNull
//    public Class<T> getExpectedClass() {
//        return expectedClass;
//    }
//
//    @Override
//    public boolean isQualified() {
//        return isQualified;
//    }
//
//    @Override
//    @NotNull
//    public FacadeTypeQualifierInterface getQualifier() {
//        return qualifier;
//    }
//
//    @Override
//    @NotNull
//    public ParameterTypeLabel getTypeLabel() {
//        return typeLabel;
//    }
//
//    @NotNull
//    public Class<T> getExpectedRawClass() {
//        return expectedRawClass;
//    }
//}
//
