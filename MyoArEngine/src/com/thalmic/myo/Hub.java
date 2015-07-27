/*
 * Decompiled with CFR 0_101.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.content.SharedPreferences
 *  android.content.SharedPreferences$Editor
 *  android.os.Build
 *  android.os.Build$VERSION
 *  android.os.Handler
 *  android.os.SystemClock
 *  android.text.TextUtils
 *  android.util.Log
 */
package com.thalmic.myo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.ControlCommand;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.GattCallback;
import com.thalmic.myo.MultiListener;
import com.thalmic.myo.Myo;
import com.thalmic.myo.MyoGatt;
import com.thalmic.myo.MyoUpdateParser;
import com.thalmic.myo.Reporter;
import com.thalmic.myo.ScanListener;
import com.thalmic.myo.internal.ble.Address;
import com.thalmic.myo.internal.ble.BleFactory;
import com.thalmic.myo.internal.ble.BleGatt;
import com.thalmic.myo.internal.ble.BleGattCallback;
import com.thalmic.myo.internal.ble.BleManager;
import com.thalmic.myo.scanner.Scanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class Hub {
    private static final String TAG = "Hub";
    private static final String PREF_INSTALL_UUID_KEY = "INSTALL_UUID";
    private static final String PREF_FILE_NAME = "com.thalmic.myosdk";
    private static final int MAX_APPLICATION_IDENTIFIER_LENGTH = 255;
    private String mApplicationIdentifier;
    private String mInstallUuid;
    private BleManager mBleManager;
    private Handler mHandler;
    private int mMyoAttachAllowance;
    private LockingPolicy mLockingPolicy;
    private Scanner mScanner;
    private final ScanListener mScanListener;
    private final HashMap<String, Myo> mKnownDevices = new HashMap();
    private final MultiListener mListeners;
    private final MyoUpdateParser mParser;
    private final GattCallback mGattCallback;
    private final MyoGatt mMyoGatt;
    private final Reporter mReporter = new Reporter();

    public static Hub getInstance() {
        return InstanceHolder.INSTANCE;
    }

    static Hub createInstanceForTests(BleManager bleManager, Handler handler, Scanner scanner, ScanListener scanListener, MultiListener listeners, MyoUpdateParser parser, GattCallback gattCallback, MyoGatt myoGatt) {
        return new Hub(bleManager, handler, scanner, scanListener, listeners, parser, gattCallback, myoGatt);
    }

    private Hub() {
        this.mListeners = new MultiListener();
        this.mParser = new MyoUpdateParser(this, this.mListeners);
        this.mGattCallback = new GattCallback(this);
        this.mMyoGatt = new MyoGatt(this);
        this.mScanListener = new ScanListener(this);
        this.mParser.setReporter(this.mReporter);
        this.mGattCallback.setUpdateParser(this.mParser);
        this.mGattCallback.setMyoGatt(this.mMyoGatt);
    }

    private Hub(BleManager bleManager, Handler handler, Scanner scanner, ScanListener scanListener, MultiListener listeners, MyoUpdateParser parser, GattCallback gattCallback, MyoGatt myoGatt) {
        this.mBleManager = bleManager;
        this.mHandler = handler;
        this.mScanner = scanner;
        this.mScanListener = scanListener;
        this.mListeners = listeners;
        this.mParser = parser;
        this.mGattCallback = gattCallback;
        this.mMyoGatt = myoGatt;
        this.setMyoAttachAllowance(1);
    }

    public boolean init(Context context) {
        return this.init(context, "");
    }

    public boolean init(Context context, String applicationIdentifier) throws IllegalArgumentException {
        if (!this.isValidApplicationIdentifier(applicationIdentifier)) {
            throw new IllegalArgumentException("Invalid application identifier");
        }
        this.mApplicationIdentifier = applicationIdentifier;
        if (this.mBleManager == null) {
            this.mBleManager = BleFactory.createBleManager(context.getApplicationContext());
        }
        if (this.mBleManager == null) {
            Log.e((String)"Hub", (String)"Could not create BleManager");
            return false;
        }
        if (!this.mBleManager.isBluetoothSupported()) {
            Log.e((String)"Hub", (String)"Bluetooth not supported");
            return false;
        }
        if (this.mScanner == null) {
            this.setMyoAttachAllowance(1);
            this.setLockingPolicy(LockingPolicy.STANDARD);
            SharedPreferences prefs = context.getSharedPreferences("com.thalmic.myosdk", 0);
            if (prefs != null) {
                this.mInstallUuid = prefs.getString("INSTALL_UUID", "");
                if (TextUtils.isEmpty((CharSequence)this.mInstallUuid)) {
                    this.mInstallUuid = UUID.randomUUID().toString();
                    prefs.edit().putString("INSTALL_UUID", this.mInstallUuid).apply();
                }
            } else {
                this.mInstallUuid = UUID.randomUUID().toString();
            }
            this.mHandler = new Handler();
            this.mScanner = new Scanner(this.mBleManager, this.mScanListener, new ScanItemClickListener());
            this.mScanner.addOnScanningStartedListener(this.mScanListener);
            this.addListener(new AbstractDeviceListener(){

                @Override
                public void onConnect(Myo myo, long timestamp) {
                    Hub.this.mScanner.getScanListAdapter().notifyDeviceChanged();
                }

                @Override
                public void onDisconnect(Myo myo, long timestamp) {
                    Hub.this.mScanner.getScanListAdapter().notifyDeviceChanged();
                }
            });
            this.mParser.setScanner(this.mScanner);
        }
        BleGatt bleGatt = this.mBleManager.getBleGatt();
        bleGatt.setBleGattCallback(this.mGattCallback);
        this.mGattCallback.setBleGatt(bleGatt);
        this.mMyoGatt.setBleManager(this.mBleManager);
        this.mScanner.setBleManager(this.mBleManager);
        return true;
    }

    public void setSendUsageData(boolean sendUsageData) {
        this.mReporter.setSendUsageData(sendUsageData);
    }

    public boolean isSendingUsageData() {
        return this.mReporter.isSendingUsageData();
    }

    public void shutdown() {
        if (Build.VERSION.SDK_INT >= 21) {
            for (Myo myo : this.mKnownDevices.values()) {
                if (!myo.isConnected()) continue;
                this.mMyoGatt.configureDataAcquisition(myo.getMacAddress(), ControlCommand.EmgMode.DISABLED, false, true);
            }
        }
        this.mBleManager.dispose();
        this.mBleManager = null;
        this.mListeners.clear();
        for (Myo myo : this.mKnownDevices.values()) {
            myo.setAttached(false);
            myo.setConnectionState(Myo.ConnectionState.DISCONNECTED);
        }
    }

    String getApplicationIdentifier() {
        return this.mApplicationIdentifier;
    }

    String getInstallUuid() {
        return this.mInstallUuid;
    }

    MyoGatt getMyoGatt() {
        return this.mMyoGatt;
    }

    public Scanner getScanner() {
        return this.mScanner;
    }

    Myo getDevice(String address) {
        return this.mKnownDevices.get(address);
    }

    public ArrayList<Myo> getConnectedDevices() {
        ArrayList<Myo> connectedMyos = new ArrayList<Myo>();
        for (Myo myo : this.mKnownDevices.values()) {
            if (!myo.isConnected()) continue;
            connectedMyos.add(myo);
        }
        return connectedMyos;
    }

    public void addListener(final DeviceListener listener) {
        if (this.mListeners.contains(listener)) {
            throw new IllegalArgumentException("Trying to add a listener that is already registered.");
        }
        this.mListeners.add(listener);
        this.mHandler.post(new Runnable(){

            @Override
            public void run() {
                long timestamp = Hub.this.now();
                for (Myo myo : Hub.this.mKnownDevices.values()) {
                    if (!myo.isAttached()) continue;
                    listener.onAttach(myo, timestamp);
                    if (myo.getConnectionState() != Myo.ConnectionState.CONNECTED) continue;
                    listener.onConnect(myo, timestamp);
                }
            }
        });
    }

    public void removeListener(DeviceListener listener) {
        this.mListeners.remove(listener);
    }

    public void attachToAdjacentMyo() {
        this.attachToAdjacentMyos(1);
    }

    public void attachToAdjacentMyos(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("The number of Myos to attach must be greater than 0.");
        }
        int numAttachedDevices = this.getMyoAttachCount();
        if (numAttachedDevices + count > this.mMyoAttachAllowance) {
            Log.w((String)"Hub", (String)String.format("Myo attach allowance is set to %d. There are currently %d attached Myos. Ignoring attach request.", this.mMyoAttachAllowance, numAttachedDevices));
            return;
        }
        this.mScanListener.attachToAdjacent(count);
    }

    public void attachByMacAddress(String macAddress) {
        int numAttachedDevices = this.getMyoAttachCount();
        if (numAttachedDevices >= this.mMyoAttachAllowance) {
            Log.w((String)"Hub", (String)String.format("Myo attach allowance is set to %d. There are currently %dattached Myo. Ignoring attach request.", this.mMyoAttachAllowance, numAttachedDevices));
            return;
        }
        Myo myo = this.getDevice(macAddress);
        if (myo != null && myo.isConnected()) {
            Log.w((String)"Hub", (String)("Already attached to the Myo at address=" + macAddress + ". Ignoring attach request."));
        } else {
            this.mScanListener.attachByMacAddress(macAddress);
        }
    }

    public void detach(String macAddress) {
        Myo myo = this.getDevice(macAddress);
        if (myo != null && myo.isAttached()) {
            this.mMyoGatt.disconnect(myo.getMacAddress());
        } else {
            Log.w((String)"Hub", (String)("No attached Myo at address=" + macAddress + ". Nothing to detach."));
        }
    }

    public long now() {
        return SystemClock.elapsedRealtime();
    }

    public void setLockingPolicy(LockingPolicy lockingPolicy) {
        this.mLockingPolicy = lockingPolicy;
    }

    public LockingPolicy getLockingPolicy() {
        return this.mLockingPolicy;
    }

    public int getMyoAttachAllowance() {
        return this.mMyoAttachAllowance;
    }

    public void setMyoAttachAllowance(int myoAttachAllowance) {
        this.mMyoAttachAllowance = myoAttachAllowance;
    }

    int getMyoAttachCount() {
        int count = 0;
        for (Myo myo : this.mKnownDevices.values()) {
            if (!myo.isAttached()) continue;
            ++count;
        }
        return count;
    }

    boolean isInitialized() {
        return this.mBleManager != null;
    }

    void addGattValueListener(GattCallback.ValueListener listener) {
        this.mGattCallback.addValueListener(listener);
    }

    Myo addKnownDevice(Address address) {
        Myo myo = this.mKnownDevices.get(address.toString());
        if (myo == null) {
            myo = new Myo(this, address);
            this.mKnownDevices.put(myo.getMacAddress(), myo);
        }
        return myo;
    }

    ArrayList<Myo> getKnownDevices() {
        return new ArrayList<Myo>(this.mKnownDevices.values());
    }

    static boolean allowedToConnectToMyo(Hub hub, String address) {
        int attachAllowance;
        Myo myo = hub.getDevice(address);
        if (myo != null && myo.isAttached()) {
            return true;
        }
        int numAttachedDevices = hub.getMyoAttachCount();
        if (numAttachedDevices >= (attachAllowance = hub.getMyoAttachAllowance())) {
            Log.w((String)"Hub", (String)String.format("Myo attach allowance is set to %d. There are currently %d attached Myos.", attachAllowance, numAttachedDevices));
            return false;
        }
        return true;
    }

    void connectToScannedMyo(String address) {
        if (!Hub.allowedToConnectToMyo(this, address)) {
            return;
        }
        boolean connecting = this.mMyoGatt.connect(address);
        if (connecting) {
            this.mScanner.getScanListAdapter().notifyDeviceChanged();
        }
    }

    void disconnectFromScannedMyo(String address) {
        this.mMyoGatt.disconnect(address);
        this.mScanner.getScanListAdapter().notifyDeviceChanged();
    }

    private boolean isValidApplicationIdentifier(String applicationIdentifier) {
        if (applicationIdentifier == null) {
            return false;
        }
        if (applicationIdentifier.isEmpty()) {
            return true;
        }
        if (applicationIdentifier.length() > 255) {
            return false;
        }
        int prevChar = 46;
        int fullStopCount = 0;
        for (int i = 0; i < applicationIdentifier.length(); ++i) {
            char c = applicationIdentifier.charAt(i);
            if ((prevChar == 46 || i == applicationIdentifier.length() - 1) && (c == '-' || c == '_' || c == '.')) {
                return false;
            }
            if (c == '.') {
                if (prevChar == 45 || prevChar == 95 || prevChar == 46 || i < 2) {
                    return false;
                }
                ++fullStopCount;
            }
            if (!(fullStopCount != 0 || Character.isLetter(c))) {
                return false;
            }
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                return false;
            }
            prevChar = c;
        }
        if (fullStopCount < 2) {
            return false;
        }
        return true;
    }

    private class ScanItemClickListener
    implements Scanner.OnMyoClickedListener {
        private ScanItemClickListener() {
        }

        @Override
        public void onMyoClicked(Myo myo) {
            switch (myo.getConnectionState()) {
                case CONNECTED: 
                case CONNECTING: {
                    Hub.this.disconnectFromScannedMyo(myo.getMacAddress());
                    break;
                }
                case DISCONNECTED: {
                    Hub.this.connectToScannedMyo(myo.getMacAddress());
                }
            }
        }
    }

    private static class InstanceHolder {
        private static final Hub INSTANCE = new Hub();

        private InstanceHolder() {
        }
    }

    public static enum LockingPolicy {
        NONE,
        STANDARD;
        

        private LockingPolicy() {
        }
    }

}
