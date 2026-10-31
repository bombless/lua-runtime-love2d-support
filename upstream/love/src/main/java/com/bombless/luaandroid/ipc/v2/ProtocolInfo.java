package com.bombless.luaandroid.ipc.v2;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;

public final class ProtocolInfo implements Parcelable {
    public String namespace = "com.bombless.luaandroid.ipc.v2";
    public int protocolMajor = 2, protocolMinor = 0;
    public String runtimeId = "", runtimeVersion = "", serviceVersion = "", buildId = "";
    public String engineId = "", engineVersion = "", luaLanguageVersion = "", luaAbi = "";
    public int maxMetadataBytes, maxInputCount, maxConcurrency, maxQueueDepth, maxEventBatchSize;
    public long maxTransferBytes, maxExecutionMillis, maxOutputBytes;
    public ArrayList<CapabilityInfo> supportedCapabilities = new ArrayList<>();

    public ProtocolInfo() {}
    private ProtocolInfo(Parcel p) {
        namespace = value(p.readString(), "com.bombless.luaandroid.ipc.v2");
        protocolMajor = p.readInt(); protocolMinor = p.readInt();
        runtimeId = value(p.readString(), ""); runtimeVersion = value(p.readString(), "");
        serviceVersion = value(p.readString(), ""); buildId = value(p.readString(), "");
        engineId = value(p.readString(), ""); engineVersion = value(p.readString(), "");
        luaLanguageVersion = value(p.readString(), ""); luaAbi = value(p.readString(), "");
        maxMetadataBytes = p.readInt(); maxTransferBytes = p.readLong(); maxInputCount = p.readInt();
        maxConcurrency = p.readInt(); maxQueueDepth = p.readInt(); maxExecutionMillis = p.readLong();
        maxOutputBytes = p.readLong(); maxEventBatchSize = p.readInt();
        supportedCapabilities = p.createTypedArrayList(CapabilityInfo.CREATOR);
        if (supportedCapabilities == null) supportedCapabilities = new ArrayList<>();
    }
    private static String value(String value, String fallback) { return value == null ? fallback : value; }
    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(namespace); p.writeInt(protocolMajor); p.writeInt(protocolMinor);
        p.writeString(runtimeId); p.writeString(runtimeVersion); p.writeString(serviceVersion); p.writeString(buildId);
        p.writeString(engineId); p.writeString(engineVersion); p.writeString(luaLanguageVersion); p.writeString(luaAbi);
        p.writeInt(maxMetadataBytes); p.writeLong(maxTransferBytes); p.writeInt(maxInputCount);
        p.writeInt(maxConcurrency); p.writeInt(maxQueueDepth); p.writeLong(maxExecutionMillis);
        p.writeLong(maxOutputBytes); p.writeInt(maxEventBatchSize); p.writeTypedList(supportedCapabilities);
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<ProtocolInfo> CREATOR = new Creator<ProtocolInfo>() {
        @Override public ProtocolInfo createFromParcel(Parcel p) { return new ProtocolInfo(p); }
        @Override public ProtocolInfo[] newArray(int size) { return new ProtocolInfo[size]; }
    };
}