/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.data.flow;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

@org.apache.avro.specific.AvroGenerated
public class Checkpoint extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 2567388784151054996L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Checkpoint\",\"namespace\":\"net.corda.data.flow\",\"fields\":[{\"name\":\"checkpoint\",\"type\":\"bytes\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<Checkpoint> ENCODER =
      new BinaryMessageEncoder<Checkpoint>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<Checkpoint> DECODER =
      new BinaryMessageDecoder<Checkpoint>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<Checkpoint> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<Checkpoint> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<Checkpoint> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<Checkpoint>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this Checkpoint to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a Checkpoint from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a Checkpoint instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static Checkpoint fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private java.nio.ByteBuffer checkpoint;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public Checkpoint() {}

  /**
   * All-args constructor.
   * @param checkpoint The new value for checkpoint
   */
  public Checkpoint(java.nio.ByteBuffer checkpoint) {
    this.checkpoint = checkpoint;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return checkpoint;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: checkpoint = (java.nio.ByteBuffer)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'checkpoint' field.
   * @return The value of the 'checkpoint' field.
   */
  public java.nio.ByteBuffer getCheckpoint() {
    return checkpoint;
  }


  /**
   * Sets the value of the 'checkpoint' field.
   * @param value the value to set.
   */
  public void setCheckpoint(java.nio.ByteBuffer value) {
    this.checkpoint = value;
  }

  /**
   * Creates a new Checkpoint RecordBuilder.
   * @return A new Checkpoint RecordBuilder
   */
  public static net.corda.data.flow.Checkpoint.Builder newBuilder() {
    return new net.corda.data.flow.Checkpoint.Builder();
  }

  /**
   * Creates a new Checkpoint RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new Checkpoint RecordBuilder
   */
  public static net.corda.data.flow.Checkpoint.Builder newBuilder(net.corda.data.flow.Checkpoint.Builder other) {
    if (other == null) {
      return new net.corda.data.flow.Checkpoint.Builder();
    } else {
      return new net.corda.data.flow.Checkpoint.Builder(other);
    }
  }

  /**
   * Creates a new Checkpoint RecordBuilder by copying an existing Checkpoint instance.
   * @param other The existing instance to copy.
   * @return A new Checkpoint RecordBuilder
   */
  public static net.corda.data.flow.Checkpoint.Builder newBuilder(net.corda.data.flow.Checkpoint other) {
    if (other == null) {
      return new net.corda.data.flow.Checkpoint.Builder();
    } else {
      return new net.corda.data.flow.Checkpoint.Builder(other);
    }
  }

  /**
   * RecordBuilder for Checkpoint instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<Checkpoint>
    implements org.apache.avro.data.RecordBuilder<Checkpoint> {

    private java.nio.ByteBuffer checkpoint;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.data.flow.Checkpoint.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.checkpoint)) {
        this.checkpoint = data().deepCopy(fields()[0].schema(), other.checkpoint);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
    }

    /**
     * Creates a Builder by copying an existing Checkpoint instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.data.flow.Checkpoint other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.checkpoint)) {
        this.checkpoint = data().deepCopy(fields()[0].schema(), other.checkpoint);
        fieldSetFlags()[0] = true;
      }
    }

    /**
      * Gets the value of the 'checkpoint' field.
      * @return The value.
      */
    public java.nio.ByteBuffer getCheckpoint() {
      return checkpoint;
    }


    /**
      * Sets the value of the 'checkpoint' field.
      * @param value The value of 'checkpoint'.
      * @return This builder.
      */
    public net.corda.data.flow.Checkpoint.Builder setCheckpoint(java.nio.ByteBuffer value) {
      validate(fields()[0], value);
      this.checkpoint = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'checkpoint' field has been set.
      * @return True if the 'checkpoint' field has been set, false otherwise.
      */
    public boolean hasCheckpoint() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'checkpoint' field.
      * @return This builder.
      */
    public net.corda.data.flow.Checkpoint.Builder clearCheckpoint() {
      checkpoint = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Checkpoint build() {
      try {
        Checkpoint record = new Checkpoint();
        record.checkpoint = fieldSetFlags()[0] ? this.checkpoint : (java.nio.ByteBuffer) defaultValue(fields()[0]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<Checkpoint>
    WRITER$ = (org.apache.avro.io.DatumWriter<Checkpoint>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<Checkpoint>
    READER$ = (org.apache.avro.io.DatumReader<Checkpoint>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    out.writeBytes(this.checkpoint);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.checkpoint = in.readBytes(this.checkpoint);

    } else {
      for (int i = 0; i < 1; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          this.checkpoint = in.readBytes(this.checkpoint);
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










