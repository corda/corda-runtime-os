/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.data.flow;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class FlowError extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -7441985034623396233L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"FlowError\",\"namespace\":\"net.corda.data.flow\",\"fields\":[{\"name\":\"errorType\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"errorMessage\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<FlowError> ENCODER =
      new BinaryMessageEncoder<FlowError>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<FlowError> DECODER =
      new BinaryMessageDecoder<FlowError>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<FlowError> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<FlowError> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<FlowError> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<FlowError>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this FlowError to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a FlowError from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a FlowError instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static FlowError fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private java.lang.String errorType;
   private java.lang.String errorMessage;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public FlowError() {}

  /**
   * All-args constructor.
   * @param errorType The new value for errorType
   * @param errorMessage The new value for errorMessage
   */
  public FlowError(java.lang.String errorType, java.lang.String errorMessage) {
    this.errorType = errorType;
    this.errorMessage = errorMessage;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return errorType;
    case 1: return errorMessage;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: errorType = value$ != null ? value$.toString() : null; break;
    case 1: errorMessage = value$ != null ? value$.toString() : null; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'errorType' field.
   * @return The value of the 'errorType' field.
   */
  public java.lang.String getErrorType() {
    return errorType;
  }


  /**
   * Sets the value of the 'errorType' field.
   * @param value the value to set.
   */
  public void setErrorType(java.lang.String value) {
    this.errorType = value;
  }

  /**
   * Gets the value of the 'errorMessage' field.
   * @return The value of the 'errorMessage' field.
   */
  public java.lang.String getErrorMessage() {
    return errorMessage;
  }


  /**
   * Sets the value of the 'errorMessage' field.
   * @param value the value to set.
   */
  public void setErrorMessage(java.lang.String value) {
    this.errorMessage = value;
  }

  /**
   * Creates a new FlowError RecordBuilder.
   * @return A new FlowError RecordBuilder
   */
  public static net.corda.data.flow.FlowError.Builder newBuilder() {
    return new net.corda.data.flow.FlowError.Builder();
  }

  /**
   * Creates a new FlowError RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new FlowError RecordBuilder
   */
  public static net.corda.data.flow.FlowError.Builder newBuilder(net.corda.data.flow.FlowError.Builder other) {
    if (other == null) {
      return new net.corda.data.flow.FlowError.Builder();
    } else {
      return new net.corda.data.flow.FlowError.Builder(other);
    }
  }

  /**
   * Creates a new FlowError RecordBuilder by copying an existing FlowError instance.
   * @param other The existing instance to copy.
   * @return A new FlowError RecordBuilder
   */
  public static net.corda.data.flow.FlowError.Builder newBuilder(net.corda.data.flow.FlowError other) {
    if (other == null) {
      return new net.corda.data.flow.FlowError.Builder();
    } else {
      return new net.corda.data.flow.FlowError.Builder(other);
    }
  }

  /**
   * RecordBuilder for FlowError instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<FlowError>
    implements org.apache.avro.data.RecordBuilder<FlowError> {

    private java.lang.String errorType;
    private java.lang.String errorMessage;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.data.flow.FlowError.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.errorType)) {
        this.errorType = data().deepCopy(fields()[0].schema(), other.errorType);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.errorMessage)) {
        this.errorMessage = data().deepCopy(fields()[1].schema(), other.errorMessage);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    /**
     * Creates a Builder by copying an existing FlowError instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.data.flow.FlowError other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.errorType)) {
        this.errorType = data().deepCopy(fields()[0].schema(), other.errorType);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.errorMessage)) {
        this.errorMessage = data().deepCopy(fields()[1].schema(), other.errorMessage);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'errorType' field.
      * @return The value.
      */
    public java.lang.String getErrorType() {
      return errorType;
    }


    /**
      * Sets the value of the 'errorType' field.
      * @param value The value of 'errorType'.
      * @return This builder.
      */
    public net.corda.data.flow.FlowError.Builder setErrorType(java.lang.String value) {
      validate(fields()[0], value);
      this.errorType = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'errorType' field has been set.
      * @return True if the 'errorType' field has been set, false otherwise.
      */
    public boolean hasErrorType() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'errorType' field.
      * @return This builder.
      */
    public net.corda.data.flow.FlowError.Builder clearErrorType() {
      errorType = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'errorMessage' field.
      * @return The value.
      */
    public java.lang.String getErrorMessage() {
      return errorMessage;
    }


    /**
      * Sets the value of the 'errorMessage' field.
      * @param value The value of 'errorMessage'.
      * @return This builder.
      */
    public net.corda.data.flow.FlowError.Builder setErrorMessage(java.lang.String value) {
      validate(fields()[1], value);
      this.errorMessage = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'errorMessage' field has been set.
      * @return True if the 'errorMessage' field has been set, false otherwise.
      */
    public boolean hasErrorMessage() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'errorMessage' field.
      * @return This builder.
      */
    public net.corda.data.flow.FlowError.Builder clearErrorMessage() {
      errorMessage = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public FlowError build() {
      try {
        FlowError record = new FlowError();
        record.errorType = fieldSetFlags()[0] ? this.errorType : (java.lang.String) defaultValue(fields()[0]);
        record.errorMessage = fieldSetFlags()[1] ? this.errorMessage : (java.lang.String) defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<FlowError>
    WRITER$ = (org.apache.avro.io.DatumWriter<FlowError>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<FlowError>
    READER$ = (org.apache.avro.io.DatumReader<FlowError>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    out.writeString(this.errorType);

    out.writeString(this.errorMessage);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.errorType = in.readString();

      this.errorMessage = in.readString();

    } else {
      for (int i = 0; i < 2; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          this.errorType = in.readString();
          break;

        case 1:
          this.errorMessage = in.readString();
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










