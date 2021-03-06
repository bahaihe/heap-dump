package com.addepar.heapdump.inspect;

import com.addepar.heapdump.inspect.inferior.Inferior;
import com.addepar.heapdump.inspect.struct.Flag;
import com.addepar.heapdump.inspect.struct.Universe;
import com.addepar.heapdump.inspect.struct.java_lang_Class;

/**
 * Top-level representation of a Hotspot VM
 */
public class Hotspot {

  private final AddressSpace addressSpace;

  private final HotspotConstants constants;

  private final HotspotStructs structs;

  private final HotspotTypes types;

  private final Universe universe;

  private final long classOopSizeOffset;

  private boolean useCompressedOops = false;
  private boolean useCompressedKlassPointers = false;
  private boolean useTLAB = false;
  private int minObjAlignmentInBytes = 8;

  private final long narrowKlassShift;
  private final long narrowKlassBase;
  private final long arrayLengthOffset;

  public Hotspot(Inferior inferior) {
    addressSpace = new AddressSpace(inferior);
    constants = new HotspotConstants(addressSpace);
    types = new HotspotTypes(addressSpace);
    structs = new HotspotStructs(addressSpace, types, constants);
    universe = structs.staticStruct(Universe.class);

    classOopSizeOffset = structs.staticStruct(java_lang_Class.class)._oop_size_offset();


    Flag staticFlag = structs.staticStruct(Flag.class);
    Flag curFlag = staticFlag.flags();
    for (int i = 0; i < staticFlag.numFlags(); i++) {
      String curName = curFlag._name();
      if ("UseCompressedClassPointers".equals(curName)) {
        useCompressedKlassPointers = addressSpace.getBoolean(curFlag._addr());
      } else if ("UseCompressedOops".equals(curName)) {
        useCompressedOops = addressSpace.getBoolean(curFlag._addr());
      } else if ("UseTLAB".equals(curName)) {
        useTLAB = addressSpace.getBoolean(curFlag._addr());
      } else if ("ObjectAlignmentInBytes".equals(curName)) {
        minObjAlignmentInBytes = addressSpace.getInt(curFlag._addr());
      }
      curFlag = structs.structAt(curFlag.getAddress() + types.getType("Flag").getSize(), Flag.class);
    }

    narrowKlassBase = universe._narrow_klass__base();
    narrowKlassShift = universe._narrow_klass__shift();

    long sizeOfArrayOopDesc = types.getType("arrayOopDesc").getSize();
    arrayLengthOffset = useCompressedKlassPointers() ? sizeOfArrayOopDesc - 4 : sizeOfArrayOopDesc;
  }

  public HotspotHeap getHeap() {
    return new HotspotHeap(this);
  }

  public AddressSpace getAddressSpace() {
    return addressSpace;
  }

  public HotspotConstants getConstants() {
    return constants;
  }

  public HotspotStructs getStructs() {
    return structs;
  }

  public HotspotTypes getTypes() {
    return types;
  }

  public Universe getUniverse() {
    return universe;
  }

  public boolean useCompressedOops() {
    return useCompressedOops;
  }

  public boolean useCompressedKlassPointers() {
    return useCompressedKlassPointers;
  }

  public long decompressKlassPointer(int compressedPointer) {
    return (Integer.toUnsignedLong(compressedPointer) << narrowKlassShift) + narrowKlassBase;
  }

  public boolean useTLAB() {
    return useTLAB;
  }

  public int getMinObjAlignmentInBytes() {
    return minObjAlignmentInBytes;
  }

  public long arrayLengthOffset() {
    return arrayLengthOffset;
  }

  public long alignUp(long val, int alignment) {
    return (val + alignment - 1) & ~(alignment - 1);
  }

  public long getClassOopSizeOffset() {
    return classOopSizeOffset;
  }

  public void reset() {
    addressSpace.reset();
  }
}
