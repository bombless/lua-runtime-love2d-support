package com.bombless.luaandroid.ipc;

// Client-side v1 discovery subset. Method order and descriptor intentionally
// match the first two methods of ipc-android's complete AIDL interface.
interface ILuaRuntimeService {
    int getApiVersion();
    String getRuntimeInfo();
}