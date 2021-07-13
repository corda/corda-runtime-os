/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.data.identity;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

@org.apache.avro.specific.AvroGenerated
public class HoldingIdentity extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 595688020675674952L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"HoldingIdentity\",\"namespace\":\"net.corda.data.identity\",\"fields\":[{\"name\":\"x500Name\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"groupId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<HoldingIdentity> ENCODER =
      new BinaryMessageEncoder<HoldingIdentity>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<HoldingIdentity> DECODER =
      new BinaryMessageDecoder<HoldingIdentity>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<HoldingIdentity> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<HoldingIdentity> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<HoldingIdentity> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<HoldingIdentity>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this HoldingIdentity to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a HoldingIdentity from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a HoldingIdentity instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static HoldingIdentity fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private java.lang.String x500Name;
   private java.lang.String groupId;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public HoldingIdentity() {}

  /**
   * All-args constructor.
   * @param x500Name The new value for x500Name
   * @param groupId The new value for groupId
   */
  public HoldingIdentity(java.lang.String x500Name, java.lang.String groupId) {
    this.x500Name = x500Name;
    this.groupId = groupId;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return x500Name;
    case 1: return groupId;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: x500Name = value$ != null ? value$.toString() : null; break;
    case 1: groupId = value$ != null ? value$.toString() : null; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'x500Name' field.
   * @return The value of the 'x500Name' field.
   */
  public java.lang.String getX500Name() {
    return x500Name;
  }


  /**
   * Sets the value of the 'x500Name' field.
   * @param value the value to set.
   */
  public void setX500Name(java.lang.String value) {
    this.x500Name = value;
  }

  /**
   * Gets the value of the 'groupId' field.
   * @return The value of the 'groupId' field.
   */
  public java.lang.String getGroupId() {
    return groupId;
  }


  /**
   * Sets the value of the 'groupId' field.
   * @param value the value to set.
   */
  public void setGroupId(java.lang.String value) {
    this.groupId = value;
  }

  /**
   * Creates a new HoldingIdentity RecordBuilder.
   * @return A new HoldingIdentity RecordBuilder
   */
  public static net.corda.data.identity.HoldingIdentity.Builder newBuilder() {
    return new net.corda.data.identity.HoldingIdentity.Builder();
  }

  /**
   * Creates a new HoldingIdentity RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new HoldingIdentity RecordBuilder
   */
  public static net.corda.data.identity.HoldingIdentity.Builder newBuilder(net.corda.data.identity.HoldingIdentity.Builder other) {
    if (other == null) {
      return new net.corda.data.identity.HoldingIdentity.Builder();
    } else {
      return new net.corda.data.identity.HoldingIdentity.Builder(other);
    }
  }

  /**
   * Creates a new HoldingIdentity RecordBuilder by copying an existing HoldingIdentity instance.
   * @param other The existing instance to copy.
   * @return A new HoldingIdentity RecordBuilder
   */
  public static net.corda.data.identity.HoldingIdentity.Builder newBuilder(net.corda.data.identity.HoldingIdentity other) {
    if (other == null) {
      return new net.corda.data.identity.HoldingIdentity.Builder();
    } else {
      return new net.corda.data.identity.HoldingIdentity.Builder(other);
    }
  }

  /**
   * RecordBuilder for HoldingIdentity instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<HoldingIdentity>
    implements org.apache.avro.data.RecordBuilder<HoldingIdentity> {

    private java.lang.String x500Name;
    private java.lang.String groupId;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.data.identity.HoldingIdentity.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.x500Name)) {
        this.x500Name = data().deepCopy(fields()[0].schema(), other.x500Name);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.groupId)) {
        this.groupId = data().deepCopy(fields()[1].schema(), other.groupId);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    /**
     * Creates a Builder by copying an existing HoldingIdentity instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.data.identity.HoldingIdentity other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.x500Name)) {
        this.x500Name = data().deepCopy(fields()[0].schema(), other.x500Name);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.groupId)) {
        this.groupId = data().deepCopy(fields()[1].schema(), other.groupId);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'x500Name' field.
      * @return The value.
      */
    public java.lang.String getX500Name() {
      return x500Name;
    }


    /**
      * Sets the value of the 'x500Name' field.
      * @param value The value of 'x500Name'.
      * @return This builder.
      */
    public net.corda.data.identity.HoldingIdentity.Builder setX500Name(java.lang.String value) {
      validate(fields()[0], value);
      this.x500Name = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'x500Name' field has been set.
      * @return True if the 'x500Name' field has been set, false otherwise.
      */
    public boolean hasX500Name() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'x500Name' field.
      * @return This builder.
      */
    public net.corda.data.identity.HoldingIdentity.Builder clearX500Name() {
      x500Name = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'groupId' field.
      * @return The value.
      */
    public java.lang.String getGroupId() {
      return groupId;
    }


    /**
      * Sets the value of the 'groupId' field.
      * @param value The value of 'groupId'.
      * @return This builder.
      */
    public net.corda.data.identity.HoldingIdentity.Builder setGroupId(java.lang.String value) {
      validate(fields()[1], value);
      this.groupId = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'groupId' field has been set.
      * @return True if the 'groupId' field has been set, false otherwise.
      */
    public boolean hasGroupId() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'groupId' field.
      * @return This builder.
      */
    public net.corda.data.identity.HoldingIdentity.Builder clearGroupId() {
      groupId = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public HoldingIdentity build() {
      try {
        HoldingIdentity record = new HoldingIdentity();
        record.x500Name = fieldSetFlags()[0] ? this.x500Name : (java.lang.String) defaultValue(fields()[0]);
        record.groupId = fieldSetFlags()[1] ? this.groupId : (java.lang.String) defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<HoldingIdentity>
    WRITER$ = (org.apache.avro.io.DatumWriter<HoldingIdentity>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<HoldingIdentity>
    READER$ = (org.apache.avro.io.DatumReader<HoldingIdentity>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    out.writeString(this.x500Name);

    out.writeString(this.groupId);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.x500Name = in.readString();

      this.groupId = in.readString();

    } else {
      for (int i = 0; i < 2; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          this.x500Name = in.readString();
          break;

        case 1:
          this.groupId = in.readString();
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










