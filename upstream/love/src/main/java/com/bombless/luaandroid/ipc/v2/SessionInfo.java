package com.bombless.luaandroid.ipc.v2;

import android.os.Parcel;
import android.os.Parcelable;

public final class SessionInfo implements Parcelable {
    public String sessionId = "";
    public long expiresAtMillis;
    public String[] grantedScopes = new String[0];

    public SessionInfo() {}
    private SessionInfo(Parcel p) {
        sessionId = p.readString(); if (sessionId == null) sessionId = "";
        expiresAtMillis = p.readLong();
        grantedScopes = p.createStringArray();
        if (grantedScopes == null) grantedScopes = new String[0];
    }
    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(sessionId == null ? "" : sessionId);
        p.writeLong(expiresAtMillis);
        p.writeStringArray(grantedScopes == null ? new String[0] : grantedScopes);
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<SessionInfo> CREATOR = new Creator<SessionInfo>() {
        @Override public SessionInfo createFromParcel(Parcel p) { return new SessionInfo(p); }
        @Override public SessionInfo[] newArray(int size) { return new SessionInfo[size]; }
    };
}