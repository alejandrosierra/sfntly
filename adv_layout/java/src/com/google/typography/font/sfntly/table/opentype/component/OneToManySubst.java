package com.google.typography.font.sfntly.table.opentype.component;

import com.google.typography.font.sfntly.data.ReadableFontData;
import com.google.typography.font.sfntly.data.WritableFontData;
import com.google.typography.font.sfntly.table.SubTable;
import com.google.typography.font.sfntly.table.opentype.CoverageTable;
import com.google.typography.font.sfntly.table.opentype.SubstSubtable;
import com.google.typography.font.sfntly.table.opentype.multiplesubst.GlyphIds;

import java.util.Iterator;

public class OneToManySubst extends SubstSubtable implements Iterable<NumRecordTable> {
  private final GlyphIds array;

  // //////////////
  // Constructors

  protected OneToManySubst(ReadableFontData data, int base, boolean dataIsCanonical) {
    super(data, base, dataIsCanonical);
    if (format != 1) {
      throw new IllegalStateException("Subt format value is " + format + " (should be 1).");
    }
    array = new GlyphIds(data, headerSize(), dataIsCanonical);
  }

  // //////////////////////////////////
  // Methods redirected to the array

  public NumRecordList recordList() {
    return array.recordList;
  }

  public NumRecordTable subTableAt(int index) {
    return array.subTableAt(index);
  }

  @Override
  public Iterator<NumRecordTable> iterator() {
    return array.iterator();
  }

  private NumRecordTable createSubTable(ReadableFontData data, boolean dataIsCanonical) {
    return array.readSubTable(data, dataIsCanonical);
  }

  // //////////////////////////////////
  // Methods specific to this class

  public CoverageTable coverage() {
    return array.coverage;
  }

  // //////////////////////////////////
  // Builder

  private static class Builder extends SubstSubtable.Builder<SubstSubtable> {

    private final GlyphIds.Builder arrayBuilder;

    // //////////////
    // Constructors

    private Builder() {
      super();
      arrayBuilder = new GlyphIds.Builder();
    }

    private Builder(ReadableFontData data, boolean dataIsCanonical) {
      super(data, dataIsCanonical);
      arrayBuilder = new GlyphIds.Builder(data, dataIsCanonical);
    }

    private Builder(SubstSubtable subTable) {
      OneToManySubst multiSubst = (OneToManySubst) subTable;
      arrayBuilder = new GlyphIds.Builder(multiSubst.array);
    }

    // /////////////////////////////
    // private methods for builders

    private int subTableCount() {
      return arrayBuilder.subTableCount();
    }

    private SubTable.Builder<? extends SubTable> builderForTag(int tag) {
      setModelChanged();
      return arrayBuilder.builderForTag(tag);
    }

    private VisibleSubTable.Builder<NumRecordTable> addBuilder() {
      setModelChanged();
      return arrayBuilder.addBuilder();
    }

    private void removeBuilderForTag(int tag) {
      setModelChanged();
      arrayBuilder.removeBuilderForTag(tag);
    }

    // ///////////////////////////////
    // private methods to serialize

    @Override
    public int subDataSizeToSerialize() {
      return arrayBuilder.subDataSizeToSerialize();
    }

    @Override
    public int subSerialize(WritableFontData newData) {
      return arrayBuilder.subSerialize(newData);
    }

    // /////////////////////////////////
    // must implement abstract methods

    @Override
    protected boolean subReadyToSerialize() {
      return true;
    }

    @Override
    public void subDataSet() {
      arrayBuilder.subDataSet();
    }

    @Override
    public OneToManySubst subBuildTable(ReadableFontData data) {
      return new OneToManySubst(data, 0, true);
    }
  }
}