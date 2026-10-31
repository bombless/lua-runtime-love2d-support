package com.bombless.luaandroid.ipc.v2;

import android.os.Parcel;
import android.os.Parcelable;

/** Stable, safe-to-send protocol error. Empty optional strings mean absent. */
public final class ErrorInfo implements Parcelable {
    public String domain = "", code = "", safeMessage = "", fieldPath = "";
    public boolean retryable;
    public String detailsJson = "{}";
    public long retryAfterMillis;
    public String correlationId = "";

    public ErrorInfo() {}
    private ErrorInfo(Parcel p) {
        String value = p.readString(); domain = value == null ? "" : value;
        value = p.readString(); code = value == null ? "" : value;
        value = p.readString(); safeMessage = value == null ? "" : value;
        retryable = p.readInt() != 0;
        value = p.readString(); fieldPath = value == null ? "" : value;
        value = p.readString(); detailsJson = value == null ? "{}" : value;
        retryAfterMillis = p.readLong();
        value = p.readString(); correlationId = value == null ? "" : value;
    }
    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(domain); p.writeString(code); p.writeString(safeMessage); p.writeInt(retryable ? 1 : 0);
        p.writeString(fieldPath); p.writeString(detailsJson); p.writeLong(retryAfterMillis); p.writeString(correlationId);
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<ErrorInfo> CREATOR = new Creator<ErrorInfo>() {
        @Override public ErrorInfo createFromParcel(Parcel p) { return new ErrorInfo(p); }
        @Override public ErrorInfo[] newArray(int size) { return new ErrorInfo[size]; }
    };
}