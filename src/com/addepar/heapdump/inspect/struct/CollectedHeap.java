package com.addepar.heapdump.inspect.struct;

import com.addepar.heapdump.inspect.FieldType;

public interface CollectedHeap extends DynamicHotspotStruct {
  @FieldType("bool")
  boolean _is_gc_active();
}
