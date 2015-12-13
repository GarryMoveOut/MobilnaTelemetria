package pl.rychlinski.damian.mobilnatelemetria;

/**
 * Defines several constants used between {@link UslugaBluetooth} and the UI.
 */
public interface Constants {
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int PID_MESSAGE = 6;

    public static final int RPM = 10;
    public static final int LOAD = 11;
    public static final int COOLANTTEMP = 12;
    public static final int SPEED = 13;
    public static final int AIRTEMP = 14;
    public static final int THROTTLE = 15;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
}
