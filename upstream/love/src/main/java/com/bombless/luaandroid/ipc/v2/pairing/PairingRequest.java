package com.bombless.luaandroid.ipc.v2.pairing;

import android.os.Parcel;
import android.os.Parcelable;

/** Separately versioned pairing input; does not alter v2.0 ClientHello. */
public final class PairingRequest implements Parcelable {
    public int schemaVersion = 1;
    public String[] requestedScopes = new String[0];

    public PairingRequest() {}
    private PairingRequest(Parcel source) {
        schemaVersion = source.readInt();
        String[] scopes = source.createStringArray();
        requestedScopes = scopes == null ? new String[0] : scopes;
    }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(schemaVersion);
        dest.writeStringArray(requestedScopes);
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<PairingRequest> CREATOR = new Creator<PairingRequest>() {
        @Override public PairingRequest createFromParcel(Parcel source) { return new PairingRequest(source); }
        @Override public PairingRequest[] newArray(int size) { return new PairingRequest[size]; }
    };
}