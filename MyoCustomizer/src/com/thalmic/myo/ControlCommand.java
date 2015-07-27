/*
 * Decompiled with CFR 0_101.
 */
package com.thalmic.myo;

import com.thalmic.myo.Myo;

import android.util.Log;

public abstract class ControlCommand
{
  private static final byte COMMAND_SET_MODE = 1;
  private static final byte COMMAND_VIBRATION = 3;
  private static final byte COMMAND_UNLOCK = 10;
  private static final byte COMMAND_USER_ACTION = 11;
  private static final byte VIBRATION_NONE = 0;
  private static final byte VIBRATION_SHORT = 1;
  private static final byte VIBRATION_MEDIUM = 2;
  private static final byte VIBRATION_LONG = 3;
  private static final byte EMG_MODE_DISABLED = 0;
  private static final byte EMG_MODE_RAW_FV = 1;
  private static final byte EMG_MODE_RAW_EMG = 2;
  private static final byte IMU_MODE_DISABLED = 0;
  private static final byte IMU_MODE_ENABLED = 1;
  private static final byte CLASSIFIER_MODE_DISABLED = 0;
  private static final byte CLASSIFIER_MODE_ENABLED = 1;
  private static final byte UNLOCK_LOCK = 0;
  private static final byte UNLOCK_TIMEOUT = 1;
  private static final byte UNLOCK_HOLD = 2;
  private static final byte USER_ACTION_GENERIC = 0;

  private ControlCommand()
  {
  }

  static byte[] createForSetMode(EmgMode streamEmg, boolean streamImu, boolean enableClassifier)
  {
    byte emgMode = 0;
    switch (streamEmg)
    {
    case FV:
    {
      emgMode = 1;
      break;
    }
    case EMG:
    {
      emgMode = 2;
    }
    }
    byte imuMode = (byte) (streamImu ? 1 : 0);
    byte classifierMode = (byte) (enableClassifier ? 1 : 0);
    return ControlCommand.createForSetMode(emgMode, imuMode, classifierMode);
  }

  private static byte[] createForSetMode(byte emgMode, byte imuMode, byte classifierMode)
  {
    byte[] controlCommand = new byte[SetMode.values().length];
    controlCommand[SetMode.COMMAND_TYPE.ordinal()] = COMMAND_SET_MODE;
    controlCommand[SetMode.PAYLOAD_SIZE.ordinal()] = (byte) (controlCommand.length - 2);
    controlCommand[SetMode.EMG_MODE.ordinal()] = emgMode;
    controlCommand[SetMode.IMU_MODE.ordinal()] = imuMode;
    controlCommand[SetMode.CLASSIFIER_MODE.ordinal()] = classifierMode;
    return controlCommand;
  }

  static byte[] createForVibrate(Myo.VibrationType vibrationType)
  {
    byte[] command = new byte[Vibration.values().length];
    command[Vibration.COMMAND_TYPE.ordinal()] = COMMAND_VIBRATION;
    command[Vibration.PAYLOAD_SIZE.ordinal()] = 1;
    command[Vibration.VIBRATION_TYPE.ordinal()] = ControlCommand.getVibrationType(vibrationType);
    return command;
  }

  private static byte getVibrationType(Myo.VibrationType vibrationType)
  {
    switch (vibrationType)
    {
    case SHORT:
    {
      return 1;
    }
    case MEDIUM:
    {
      return 2;
    }
    case LONG:
    {
      return 3;
    }
    }
    return 0;
  }

  static byte[] createForUnlock(Myo.UnlockType unlockType)
  {
    byte[] command = new byte[Unlock.values().length];
    command[Unlock.COMMAND_TYPE.ordinal()] = COMMAND_UNLOCK;
    command[Unlock.PAYLOAD_SIZE.ordinal()] = 1;
    command[Unlock.UNLOCK_TYPE.ordinal()] = ControlCommand.getUnlockTypeType(unlockType);
    return command;
  }

  private static byte getUnlockTypeType(Myo.UnlockType unlockType)
  {
    if (unlockType == null)
    {
      return 0;
    }
    switch (unlockType)
    {
    case TIMED:
    {
      return 1;
    }
    case HOLD:
    {
      return 2;
    }
    }
    throw new IllegalArgumentException("Unknown UnlockType: " + (Object) unlockType);
  }

  static byte[] createForUserAction()
  {
    byte[] command = new byte[Unlock.values().length];
    command[UserAction.COMMAND_TYPE.ordinal()] = COMMAND_USER_ACTION;
    command[UserAction.PAYLOAD_SIZE.ordinal()] = 1;
    command[UserAction.USER_ACTION.ordinal()] = 0;
    return command;
  }

  /*
   * static byte[] createForBatteryLevelRequest() { Log.e("myo info",
   * "send request"); byte[] command = new byte[5];
   * command[UserAction.COMMAND_TYPE.ordinal()] = 3;
   * command[UserAction.PAYLOAD_SIZE.ordinal()] = 3; command[2] = 3; command[3]
   * = 1; command[4] = 3; // command[5] = 0x19; command[6] = 3; command[7] = 3;
   * command[8] = 1; command[9] = 3; return command; }
   */

  public static byte[] createForTurnOffForTransport()
  {
    byte[] command = new byte[2];
    command[0] = 4;
    command[1] = 0;
    return command;
  }

  public static byte[] createForSetLightsColors(int red, int green, int blue, int red2, int green2, int blue2)
  {
    byte[] command = new byte[8];
    command[0] = 6;
    command[1] = 6;
    command[2] = (byte) red;
    command[3] = (byte) green;
    command[4] = (byte) blue;
    command[5] = (byte) red2;
    command[6] = (byte) green2;
    command[7] = (byte) blue2;
    return command;
  }

  public static byte[] createForSetSleepMode(SleepMode i)
  {
    byte[] command = new byte[3];
    command[0] = 9;
    command[1] = 1;
    command[2] = (byte) (i == SleepMode.NORMAL ? 0 : 1);
    return command;
  }

  private static enum UserAction
  {
    COMMAND_TYPE, PAYLOAD_SIZE, USER_ACTION
  }

  private static enum Unlock
  {
    COMMAND_TYPE, PAYLOAD_SIZE, UNLOCK_TYPE
  }

  private static enum Vibration
  {
    COMMAND_TYPE, PAYLOAD_SIZE, VIBRATION_TYPE
  }

  private static enum SetMode
  {
    COMMAND_TYPE, PAYLOAD_SIZE, EMG_MODE, IMU_MODE, CLASSIFIER_MODE
  }

  public static enum EmgMode
  {
    DISABLED, FV, EMG
  }

  public static enum SleepMode
  {
    NORMAL, NEVER_SLEEP
  }
}
