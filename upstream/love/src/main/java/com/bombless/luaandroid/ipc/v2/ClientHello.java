package com.bombless.luaandroid.ipc.v2;

import android.os.Parcel;
import android.os.Parcelable;

public final class ClientHello implements Parcelable {
    public String clientVersion = "", buildId = "", nonce = java.util.UUID.randomUUID().toString();
    public int minProtocolMajor = 2, maxProtocolMajor = 2, minProtocolMinor = 0, maxProtocolMinor = 0;
    public String[] requestedCapabilities = new String[0];

    public ClientHello() {}
    private ClientHello(Parcel p) {
        clientVersion = value(p.readString()); buildId = value(p.readString());
        minProtocolMajor = p.readInt(); maxProtocolMajor = p.readInt();
        minProtocolMinor = p.readInt(); maxProtocolMinor = p.readInt();
        requestedCapabilities = p.createStringArray();
        if (requestedCapabilities == null) requestedCapabilities = new String[0];
        nonce = value(p.readString());
    }
    private static String value(String value) { return value == null ? "" : value; }
    @Override public void writeToParcel(Parcel p, int flags) {
        p.writeString(value(clientVersion)); p.writeString(value(buildId));
        p.writeInt(minProtocolMajor); p.writeInt(maxProtocolMajor);
        p.writeInt(minProtocolMinor); p.writeInt(maxProtocolMinor);
        p.writeStringArray(requestedCapabilities == null ? new String[0] : requestedCapabilities);
        p.writeString(value(nonce));
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<ClientHello> CREATOR = new Creator<ClientHello>() {
        @Override public ClientHello createFromParcel(Parcel p) { return new ClientHello(p); }
        @Override public ClientHello[] newArray(int size) { return new ClientHello[size]; }
    };
}