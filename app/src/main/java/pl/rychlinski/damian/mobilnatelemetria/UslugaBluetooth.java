package pl.rychlinski.damian.mobilnatelemetria;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class UslugaBluetooth extends Service {
    private static final String TAG = "BluetoothChatService";

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private int mState;
    private ConnectedThread mConnectedThread;
    private ConnectThread mConnectThread;
    private Context context;

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public static final List<String> CMD_ASK_LIST = Arrays.asList("01 0C", "01 04", "01 05", "01 0D", "01 0F", "01 11");

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public UslugaBluetooth(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        this.context = context;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(String)
     */
    public void write(String out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.addToQueue(out);
    }

    public void beginTelemetry(){
        ConnectedThread r;
        synchronized (this){
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.startTelemetry();
    }

    public void preSetupELM(){
        ConnectedThread r;
        synchronized (this){
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.preSetup();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private Queue<byte[]> cmdQueue;
        private boolean preSetupON;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            cmdQueue = new LinkedList<>();

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
            int cmdPointer = 0;

            StringBuilder readMessage = new StringBuilder();
            // Keep listening to the InputStream while connected
            while (!Thread.interrupted()) {
                try {
                    // Read from the InputStream (wiadomości od OBDII
                    bytes = mmInStream.read(buffer);
                    String readed = new String(buffer, 0, bytes);
                    readMessage.append(readed);

                    if (readed.contains("\n") || readed.contains("\r") || readed.contains("0d")) {
                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget();

                        List<String> listBytesAnsw = Arrays.asList(readed.trim().split("\\s+"));
                        try {
                            //Obroty silnika
                            if (listBytesAnsw.get(1).equals("0C")) {
                                int A = Integer.parseInt(listBytesAnsw.get(2), 16);
                                int B = Integer.parseInt(listBytesAnsw.get(3), 16);
                                float rpm = (float) (A * 255 + B) / 4;
                                mHandler.obtainMessage(Constants.RPM, String.format("%.2f", rpm)).sendToTarget();

                                Intent intent = new Intent();
                                intent.setAction("pl.rychlinski.damian.mobilnatelemetria.pid.rpm");
                                intent.putExtra("RPM", rpm);
                                context.sendBroadcast(intent);
                            }

                            //Obciążenie silnika
                            if (listBytesAnsw.get(1).equals("04")) {
                                int A = Integer.parseInt(listBytesAnsw.get(2), 16);
                                float load = (float) A * 100 / 255;
                                mHandler.obtainMessage(Constants.LOAD, String.format("%.2f", load)).sendToTarget();

                                Intent intent = new Intent();
                                intent.setAction("pl.rychlinski.damian.mobilnatelemetria.pid.load");
                                intent.putExtra("LOAD", load);
                                context.sendBroadcast(intent);
                            }

                            //Temperatura płynu chłodniczego
                            if (listBytesAnsw.get(1).equals("05")) {
                                int A = Integer.parseInt(listBytesAnsw.get(2), 16);
                                int coolantTemp = A - 40;
                                mHandler.obtainMessage(Constants.COOLANTTEMP, Integer.toString(coolantTemp)).sendToTarget();

                                Intent intent = new Intent();
                                intent.setAction("pl.rychlinski.damian.mobilnatelemetria.pid.coolanttemp");
                                intent.putExtra("COOLANTTEMP", coolantTemp);
                                context.sendBroadcast(intent);
                            }

                            //Prędkość pojazdu
                            if (listBytesAnsw.get(1).equals("0D")) {
                                int speed = Integer.parseInt(listBytesAnsw.get(2), 16);
                                mHandler.obtainMessage(Constants.SPEED, Integer.toString(speed)).sendToTarget();
                                //addToQueue("01 0D");
                            }

                            //Temperatura powietrza zassanego
                            if (listBytesAnsw.get(1).equals("0F")) {
                                int A = Integer.parseInt(listBytesAnsw.get(2), 16);
                                int airTemp = A - 40;
                                mHandler.obtainMessage(Constants.AIRTEMP, Integer.toString(airTemp)).sendToTarget();
                                //addToQueue("01 0F");
                            }

                            //Pozycja przepustnicy
                            if (listBytesAnsw.get(1).equals("11")) {
                                int A = Integer.parseInt(listBytesAnsw.get(2), 16);
                                float throttle = (float) A * 100 / 255;
                                mHandler.obtainMessage(Constants.THROTTLE, String.format("%.2f", throttle)).sendToTarget();
                                //addToQueue("01 11");
                            }
                        }catch (IndexOutOfBoundsException ex){
                            Log.e(TAG, "OutOfBound", ex);
                            Log.i("Readed:", readed);
                        }


                        if(readed.contains(">")){
                            if(!preSetupON){
                                addToQueue(CMD_ASK_LIST.get(cmdPointer));
                                cmdPointer++;
                                if(cmdPointer>5) cmdPointer = 0;
                                fireCmd();
                            }else{
                                fireCmd();
                            }

                        }
                        readMessage.setLength(0);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Komenda rozpoczynająca monitorowanie parametrów pracy.
         */
        public void startTelemetry(){
            //addToQueue("01 0C; 01 04; 01 05; 01 0D; 01 0F; 01 11");
            //fireCmd();
            preSetupON = false;
            addToQueue("01 0C");
            fireCmd();
        }

        /**
         * Przesłanie koment ustawiających mikrokontroler ELM
         */
        public void preSetup(){
            preSetupON = true;
            addToQueue("atz; atl0; ate0; ath0; atat1; atstff; atsp0; atdp");
            fireCmd();
        }

        /**
         * Write to the connected OutStream. Wiadomości do OBDII
         * Pobiera komende z kolejki następnie wysyła ją do OBD oraz do widoku.
         *
         */
        public void fireCmd() {
            try {
                byte[] buffer = cmdQueue.poll();

                if(buffer != null) {
                    mmOutStream.write(buffer);

                    // Share the sent message back to the UI Activity
                    //mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception during fire command", e);
            }
        }

        /**
         * Dopisuje komende do kolejki która później zostanie wysłana do OBDII
         *
         * @param buffer The bytes to write
         */
        public void addToQueue(String buffer) {
            try {
                List<String> items = Arrays.asList(buffer.split("\\s*;\\s*"));
                //TODO: do optymalizacji
                for(int i=0;i<items.size();i++){
                    items.set(i,items.get(i)+"\r");
                }

                for(int i=0;i<items.size();i++){
                    cmdQueue.offer(items.get(i).getBytes());
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Exception during write queue list", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (UslugaBluetooth.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        //UslugaBluetooth.this.start();
        //Serwer ?
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(STATE_NONE);
    }
}
