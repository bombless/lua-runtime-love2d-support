package com.bombless.luaandroid.ipc.v2.pairing;

import android.os.Parcel;
import android.os.Parcelable;

/** Opaque request handle and lifecycle state only; contains no Runtime metadata. */
public final class PairingRequestResult implements Parcelable {
    public int schemaVersion = 1;
    public String requestId = "";
    public String status = "";
    public long expiresAtElapsedRealtime = 0L;

    public PairingRequestResult() {}
    private PairingRequestResult(Parcel source) {
        schemaVersion = source.readInt();
        String id = source.readString(); requestId = id == null ? "" : id;
        String state = source.readString(); status = state == null ? "" : state;
        expiresAtElapsedRealtime = source.readLong();
    }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(schemaVersion);
        dest.writeString(requestId);
        dest.writeString(status);
        dest.writeLong(expiresAtElapsedRealtime);
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<PairingRequestResult> CREATOR = new Creator<PairingRequestResult>() {
        @Override public PairingRequestResult createFromParcel(Parcel source) { return new PairingRequestResult(source); }
        @Override public PairingRequestResult[] newArray(int size) { return new PairingRequestResult[size]; }
    };
}