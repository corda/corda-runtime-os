/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.p2p;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class SessionPartitions extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 6744610084481961007L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"SessionPartitions\",\"namespace\":\"net.corda.p2p\",\"fields\":[{\"name\":\"partitions\",\"type\":{\"type\":\"array\",\"items\":\"int\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<SessionPartitions> ENCODER =
      new BinaryMessageEncoder<SessionPartitions>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<SessionPartitions> DECODER =
      new BinaryMessageDecoder<SessionPartitions>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<SessionPartitions> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<SessionPartitions> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<SessionPartitions> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<SessionPartitions>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this SessionPartitions to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a SessionPartitions from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a SessionPartitions instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static SessionPartitions fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private java.util.List<java.lang.Integer> partitions;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public SessionPartitions() {}

  /**
   * All-args constructor.
   * @param partitions The new value for partitions
   */
  public SessionPartitions(java.util.List<java.lang.Integer> partitions) {
    this.partitions = partitions;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return partitions;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: partitions = (java.util.List<java.lang.Integer>)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'partitions' field.
   * @return The value of the 'partitions' field.
   */
  public java.util.List<java.lang.Integer> getPartitions() {
    return partitions;
  }


  /**
   * Sets the value of the 'partitions' field.
   * @param value the value to set.
   */
  public void setPartitions(java.util.List<java.lang.Integer> value) {
    this.partitions = value;
  }

  /**
   * Creates a new SessionPartitions RecordBuilder.
   * @return A new SessionPartitions RecordBuilder
   */
  public static net.corda.p2p.SessionPartitions.Builder newBuilder() {
    return new net.corda.p2p.SessionPartitions.Builder();
  }

  /**
   * Creates a new SessionPartitions RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new SessionPartitions RecordBuilder
   */
  public static net.corda.p2p.SessionPartitions.Builder newBuilder(net.corda.p2p.SessionPartitions.Builder other) {
    if (other == null) {
      return new net.corda.p2p.SessionPartitions.Builder();
    } else {
      return new net.corda.p2p.SessionPartitions.Builder(other);
    }
  }

  /**
   * Creates a new SessionPartitions RecordBuilder by copying an existing SessionPartitions instance.
   * @param other The existing instance to copy.
   * @return A new SessionPartitions RecordBuilder
   */
  public static net.corda.p2p.SessionPartitions.Builder newBuilder(net.corda.p2p.SessionPartitions other) {
    if (other == null) {
      return new net.corda.p2p.SessionPartitions.Builder();
    } else {
      return new net.corda.p2p.SessionPartitions.Builder(other);
    }
  }

  /**
   * RecordBuilder for SessionPartitions instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<SessionPartitions>
    implements org.apache.avro.data.RecordBuilder<SessionPartitions> {

    private java.util.List<java.lang.Integer> partitions;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.p2p.SessionPartitions.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.partitions)) {
        this.partitions = data().deepCopy(fields()[0].schema(), other.partitions);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
    }

    /**
     * Creates a Builder by copying an existing SessionPartitions instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.p2p.SessionPartitions other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.partitions)) {
        this.partitions = data().deepCopy(fields()[0].schema(), other.partitions);
        fieldSetFlags()[0] = true;
      }
    }

    /**
      * Gets the value of the 'partitions' field.
      * @return The value.
      */
    public java.util.List<java.lang.Integer> getPartitions() {
      return partitions;
    }


    /**
      * Sets the value of the 'partitions' field.
      * @param value The value of 'partitions'.
      * @return This builder.
      */
    public net.corda.p2p.SessionPartitions.Builder setPartitions(java.util.List<java.lang.Integer> value) {
      validate(fields()[0], value);
      this.partitions = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'partitions' field has been set.
      * @return True if the 'partitions' field has been set, false otherwise.
      */
    public boolean hasPartitions() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'partitions' field.
      * @return This builder.
      */
    public net.corda.p2p.SessionPartitions.Builder clearPartitions() {
      partitions = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SessionPartitions build() {
      try {
        SessionPartitions record = new SessionPartitions();
        record.partitions = fieldSetFlags()[0] ? this.partitions : (java.util.List<java.lang.Integer>) defaultValue(fields()[0]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<SessionPartitions>
    WRITER$ = (org.apache.avro.io.DatumWriter<SessionPartitions>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<SessionPartitions>
    READER$ = (org.apache.avro.io.DatumReader<SessionPartitions>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    long size0 = this.partitions.size();
    out.writeArrayStart();
    out.setItemCount(size0);
    long actualSize0 = 0;
    for (java.lang.Integer e0: this.partitions) {
      actualSize0++;
      out.startItem();
      out.writeInt(e0);
    }
    out.writeArrayEnd();
    if (actualSize0 != size0)
      throw new java.util.ConcurrentModificationException("Array-size written was " + size0 + ", but element count was " + actualSize0 + ".");

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      long size0 = in.readArrayStart();
      java.util.List<java.lang.Integer> a0 = this.partitions;
      if (a0 == null) {
        a0 = new SpecificData.Array<java.lang.Integer>((int)size0, SCHEMA$.getField("partitions").schema());
        this.partitions = a0;
      } else a0.clear();
      SpecificData.Array<java.lang.Integer> ga0 = (a0 instanceof SpecificData.Array ? (SpecificData.Array<java.lang.Integer>)a0 : null);
      for ( ; 0 < size0; size0 = in.arrayNext()) {
        for ( ; size0 != 0; size0--) {
          java.lang.Integer e0 = (ga0 != null ? ga0.peek() : null);
          e0 = in.readInt();
          a0.add(e0);
        }
      }

    } else {
      for (int i = 0; i < 1; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          long size0 = in.readArrayStart();
          java.util.List<java.lang.Integer> a0 = this.partitions;
          if (a0 == null) {
            a0 = new SpecificData.Array<java.lang.Integer>((int)size0, SCHEMA$.getField("partitions").schema());
            this.partitions = a0;
          } else a0.clear();
          SpecificData.Array<java.lang.Integer> ga0 = (a0 instanceof SpecificData.Array ? (SpecificData.Array<java.lang.Integer>)a0 : null);
          for ( ; 0 < size0; size0 = in.arrayNext()) {
            for ( ; size0 != 0; size0--) {
              java.lang.Integer e0 = (ga0 != null ? ga0.peek() : null);
              e0 = in.readInt();
              a0.add(e0);
            }
          }
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










