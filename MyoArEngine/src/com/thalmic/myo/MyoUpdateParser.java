/*
 * Decompiled with CFR 0_101.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.thalmic.myo;

import android.util.Log;
import com.thalmic.myo.Arm;
import com.thalmic.myo.ClassifierEvent;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.GattCallback;
import com.thalmic.myo.GattConstants;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.MyoGatt;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.Reporter;
import com.thalmic.myo.Vector3;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.Scanner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

class MyoUpdateParser
implements GattCallback.UpdateParser {
    private static final String TAG = "MyoUpdateParser";
    static final int IMU_EXPECTED_BYTE_LENGTH = 20;
    private static final double ORIENTATION_CONVERSION_CONSTANT = 16384.0;
    private static final double ACCELERATION_CONVERSION_CONSTANT = 2048.0;
    private static final double GYRO_CONVERSION_CONSTANT = 16.0;
    private Hub mHub;
    private DeviceListener mListener;
    private Scanner mScanner;
    private Reporter mReporter;

    MyoUpdateParser(Hub hub, DeviceListener listener) {
        this.mHub = hub;
        this.mListener = listener;
    }

    void setListener(DeviceListener listener) {
        this.mListener = listener;
    }

    void setScanner(Scanner scanner) {
        this.mScanner = scanner;
    }

    void setReporter(Reporter reporter) {
        this.mReporter = reporter;
    }

    @Override
    public void onMyoConnected(Myo myo) {
        long now = this.mHub.now();
        if (!myo.isAttached()) {
            myo.setAttached(true);
            this.mListener.onAttach(myo, now);
            this.mReporter.sendMyoEvent(this.mHub.getApplicationIdentifier(), this.mHub.getInstallUuid(), "AttachedMyo", myo);
        }
        this.setMyoConnectionState(myo, Myo.ConnectionState.CONNECTED);
        this.mListener.onConnect(myo, now);
    }

    @Override
    public void onMyoDisconnected(Myo myo) {
        long now = this.mHub.now();
        this.setMyoConnectionState(myo, Myo.ConnectionState.DISCONNECTED);
        if (myo.getPose() != Pose.UNKNOWN) {
            myo.setCurrentPose(Pose.UNKNOWN);
            this.mListener.onPose(myo, now, myo.getPose());
        }
        if (myo.isUnlocked()) {
            myo.setUnlocked(false);
            this.mListener.onLock(myo, now);
        }
        if (myo.getArm() != Arm.UNKNOWN) {
            myo.setCurrentArm(Arm.UNKNOWN);
            myo.setCurrentXDirection(XDirection.UNKNOWN);
            this.mListener.onArmUnsync(myo, now);
        }
        this.mListener.onDisconnect(myo, now);
        if (myo.isAttached()) {
            this.mHub.getMyoGatt().connect(myo.getMacAddress(), true);
        } else {
            this.mListener.onDetach(myo, now);
            this.mReporter.sendMyoEvent(this.mHub.getApplicationIdentifier(), this.mHub.getInstallUuid(), "DetachedMyo", myo);
        }
    }

    @Override
    public void onCharacteristicChanged(Myo myo, UUID uuid, byte[] value) {
        if (GattConstants.IMU_DATA_CHAR_UUID.equals(uuid)) {
            this.notifyMotionData(myo, value);
        } else if (GattConstants.CLASSIFIER_EVENT_CHAR_UUID.equals(uuid)) {
            this.onClassifierEventData(myo, value);
        } else {
        	Log.e("myo info", "something else");
        }
    }

    @Override
    public void onReadRemoteRssi(Myo myo, int rssi) {
        this.mListener.onRssi(myo, this.mHub.now(), rssi);
    }

    private void onClassifierEventData(Myo myo, byte[] classifierEventData) {
        try {
            ClassifierEvent classifierEvent = new ClassifierEvent(classifierEventData);
            switch (classifierEvent.getType()) {
                case ARM_SYNCED: {
                    this.onArmSync(myo, classifierEvent);
                    break;
                }
                case ARM_UNSYNCED: {
                    this.onArmUnsync(myo);
                    break;
                }
                case POSE: {
                    this.onPose(myo, classifierEvent.getPose());
                    break;
                }
                case UNLOCKED: {
                    myo.setUnlocked(true);
                    this.mListener.onUnlock(myo, this.mHub.now());
                    break;
                }
                case LOCKED: {
                    myo.setUnlocked(false);
                    this.mListener.onLock(myo, this.mHub.now());
                    break;
                                }
                case WARMUP_COMPLETE: {
                	this.mListener.onWarmupComplete(myo, this.mHub.now(), classifierEvent.getWarmupResult());
                    break;
                }
            }
        }
        catch (IllegalArgumentException e) {
            Log.e((String)"MyoUpdateParser", (String)"Received malformed classifier event.", (Throwable)e);
        }
    }

    private void onArmSync(Myo myo, ClassifierEvent classifierEvent) {
        long now = this.mHub.now();
        myo.setCurrentArm(classifierEvent.getArm());
        myo.setCurrentXDirection(classifierEvent.getXDirection());
        this.mListener.onArmSync(myo, now, myo.getArm(), myo.getXDirection(), 0.0f, classifierEvent.getWarmupState());
        this.mReporter.sendMyoEvent(this.mHub.getApplicationIdentifier(), this.mHub.getInstallUuid(), "SyncedMyo", myo);
    }

    private void onArmUnsync(Myo myo) {
        long now = this.mHub.now();
        myo.setCurrentArm(Arm.UNKNOWN);
        myo.setCurrentXDirection(XDirection.UNKNOWN);
        this.mListener.onArmUnsync(myo, now);
        this.mReporter.sendMyoEvent(this.mHub.getApplicationIdentifier(), this.mHub.getInstallUuid(), "UnsyncedMyo", myo);
    }

    private void onPose(Myo myo, Pose pose) {
        if (this.mHub.getLockingPolicy() == Hub.LockingPolicy.NONE || myo.isUnlocked()) {
            myo.setCurrentPose(pose);
            this.mListener.onPose(myo, this.mHub.now(), myo.getPose());
        } else if (this.mHub.getLockingPolicy() == Hub.LockingPolicy.STANDARD && pose == myo.getUnlockPose()) {
            myo.unlock(Myo.UnlockType.TIMED);
        }
    }

    private void notifyMotionData(Myo myo, byte[] imuData) {
    	Log.e("myo info", "notifyMotionData: " + imuData.length);
        if (imuData.length >= 20) {
            Quaternion rotation = MyoUpdateParser.readQuaternion(imuData);
            Vector3 accel = MyoUpdateParser.readAcceleration(imuData);
            Vector3 gyro = MyoUpdateParser.readGyroscope(imuData);
            long time = this.mHub.now();
            this.mListener.onOrientationData(myo, time, rotation);
            this.mListener.onAccelerometerData(myo, time, accel);
            this.mListener.onGyroscopeData(myo, time, gyro);
        }
    }

    private void setMyoConnectionState(Myo myo, Myo.ConnectionState connectionState) {
        myo.setConnectionState(connectionState);
        if (this.mScanner != null) {
            this.mScanner.getScanListAdapter().notifyDeviceChanged();
        }
    }

    private static Quaternion readQuaternion(byte[] imuData) {
        double w = (double)MyoUpdateParser.getShort(imuData, 0) / 16384.0;
        double x = (double)MyoUpdateParser.getShort(imuData, 2) / 16384.0;
        double y = (double)MyoUpdateParser.getShort(imuData, 4) / 16384.0;
        double z = (double)MyoUpdateParser.getShort(imuData, 6) / 16384.0;
        return new Quaternion(x, y, z, w);
    }

    private static Vector3 readAcceleration(byte[] imuData) {
        double x = (double)MyoUpdateParser.getShort(imuData, 8) / 2048.0;
        double y = (double)MyoUpdateParser.getShort(imuData, 10) / 2048.0;
        double z = (double)MyoUpdateParser.getShort(imuData, 12) / 2048.0;
        return new Vector3(x, y, z);
    }

    private static Vector3 readGyroscope(byte[] imuData) {
        double x = (double)MyoUpdateParser.getShort(imuData, 14) / 16.0;
        double y = (double)MyoUpdateParser.getShort(imuData, 16) / 16.0;
        double z = (double)MyoUpdateParser.getShort(imuData, 18) / 16.0;
        return new Vector3(x, y, z);
    }

    static short getShort(byte[] array, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort(offset);
    }

}
