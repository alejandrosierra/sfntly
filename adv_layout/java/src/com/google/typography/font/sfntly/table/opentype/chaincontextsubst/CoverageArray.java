package com.google.typography.font.sfntly.table.opentype.chaincontextsubst;

import com.google.typography.font.sfntly.data.ReadableFontData;
import com.google.typography.font.sfntly.table.opentype.CoverageTableNew;
import com.google.typography.font.sfntly.table.opentype.component.NumRecordList;
import com.google.typography.font.sfntly.table.opentype.component.OffsetRecordTable;
import com.google.typography.font.sfntly.table.opentype.component.VisibleBuilder;

public class CoverageArray extends OffsetRecordTable<CoverageTableNew> {
  public static final int FIELD_COUNT = 0;

  public CoverageArray(ReadableFontData data, int base, boolean dataIsCanonical) {
    super(data, base, dataIsCanonical);
  }

  public CoverageArray(NumRecordList records) {
    super(records);
  }

  @Override
  public CoverageTableNew readSubTable(ReadableFontData data, boolean dataIsCanonical) {
    return new CoverageTableNew(data, 0, dataIsCanonical);
  }

  public static class Builder extends OffsetRecordTable.Builder<CoverageArray, CoverageTableNew> {

    public Builder() {
      super();
    }

    public Builder(ReadableFontData data, boolean dataIsCanonical) {
      super(data, dataIsCanonical);
    }

    public Builder(CoverageArray table) {
      super(table);
    }

    public Builder(NumRecordList records) {
      super(records);
    }

    @Override
    protected CoverageArray readTable(ReadableFontData data, int base, boolean dataIsCanonical) {
      return new CoverageArray(data, base, dataIsCanonical);
    }

    @Override
    protected VisibleBuilder<CoverageTableNew> createSubTableBuilder() {
      return new CoverageTableNew.Builder();
    }

    @Override
    protected VisibleBuilder<CoverageTableNew> createSubTableBuilder(
        ReadableFontData data, boolean dataIsCanonical) {
      return new CoverageTableNew.Builder(data, dataIsCanonical);
    }

    @Override
    protected VisibleBuilder<CoverageTableNew> createSubTableBuilder(CoverageTableNew subTable) {
      return new CoverageTableNew.Builder(subTable);
    }

    @Override
    protected void initFields() {
    }

    @Override
    public int fieldCount() {
      return FIELD_COUNT;
    }
  }

  @Override
  public int fieldCount() {
    return FIELD_COUNT;
  }
}