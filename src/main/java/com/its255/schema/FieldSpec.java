package com.its255.schema;

public final class FieldSpec {
    public final String name;       // COBOL/biz name
    public final int start1Based;   // COBOL-style 1-based position
    public final int lengthBytes;   // bytes for ALPHA/NUMERIC_TEXT; bytes for PACKED/BINARY
    public final FieldType type;
    public final int scale;         // decimal places for packed/zoned; else 0
    
    
    public FieldSpec(String name, int start1Based, int lengthBytes, FieldType type) {
        this(name, start1Based, lengthBytes, type, 0);
    }
    public FieldSpec(String name, int start1Based, int lengthBytes, FieldType type, int scale) {
        this.name = name;
        this.start1Based = start1Based;
        this.lengthBytes = lengthBytes;
        this.type = type;
        this.scale = scale;
    }
}

