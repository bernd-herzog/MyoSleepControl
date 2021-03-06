/*
 * Decompiled with CFR 0_101.
 */
package com.thalmic.myo.internal.ble;

import com.thalmic.myo.internal.ble.Address;
import java.util.UUID;

public abstract class BleGattCallback {
    public void onDeviceConnectionFailed(Address address) {
    }

    public void onDeviceConnected(Address address) {
    }

    public void onDeviceDisconnected(Address address) {
    }

    public void onServicesDiscovered(Address address, boolean success) {
    }

    public void onCharacteristicRead(Address address, UUID uuid, byte[] value, boolean success) {
    }

    void onCharacteristicWrite(Address address, UUID uuid, boolean success) {
    }

    void onCharacteristicChanged(Address address, UUID uuid, byte[] value) {
    }
}
