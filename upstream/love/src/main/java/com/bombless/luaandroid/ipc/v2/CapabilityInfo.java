package com.bombless.luaandroid.ipc.v2;

import android.os.Parcel;
import android.os.Parcelable;

public final class CapabilityInfo implements Parcelable {
    public String id = "", limitsJson = "{}";
    public int schemaVersion = 1;
    public CapabilityInfo() {}
    private CapabilityInfo(Parcel p) {
        String value = p.readString(); id = value == null ? "" : value;
        schemaVersion = p.readInt();
        value = p.readString(); limitsJson = value == null ? "{}" : value;
    }
    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(id); p.writeInt(schemaVersion); p.writeString(limitsJson);
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<CapabilityInfo> CREATOR = new Creator<CapabilityInfo>() {
        @Override public CapabilityInfo createFromParcel(Parcel p) { return new CapabilityInfo(p); }
        @Override public CapabilityInfo[] newArray(int size) { return new CapabilityInfo[size]; }
    };
}