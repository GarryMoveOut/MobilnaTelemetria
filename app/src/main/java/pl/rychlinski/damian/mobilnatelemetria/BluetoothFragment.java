package pl.rychlinski.damian.mobilnatelemetria;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;

import org.w3c.dom.Text;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;


public class BluetoothFragment extends android.support.v4.app.Fragment {
    private static final String TAG = "BluetoothFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    private BluetoothAdapter mBluetoothAdapter = null;
    private UslugaBluetooth mChatService = null;
    private StringBuffer mOutStringBuffer;
    private String mConnectedDeviceName = null;

    private float rpm;
    private float load;
    private int coolant;
    private int speed;
    private int airTemp;
    private float throttle;

    private Button btnPreSetup;
    private Button btnStartTelemetry;
    private TextView tvRpm;
    private TextView tvLoad;
    private TextView tvCoolantTemp;
    private TextView tvSpeed;
    private TextView tvAirTemp;
    private TextView tvThrottle;

    private static final int HISTORY_SIZE = 300;            // number of points to plot in history
    private XYPlot aprHistoryPlot = null;                   // wykres rpm
    private SimpleXYSeries azimuthHistorySeries = null;     // seria RPM
    private Redrawer redrawer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }

        azimuthHistorySeries = new SimpleXYSeries("Az.");
        azimuthHistorySeries.useImplicitXVals();

        //Ustawienia wykresu
        aprHistoryPlot.setRangeBoundaries(800, 5000, BoundaryMode.FIXED);
        aprHistoryPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
        aprHistoryPlot.addSeries(azimuthHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 100, 200), null, null, null));
        //aprHistoryPlot.addSeries(pitchHistorySeries, new LineAndPointFormatter(Color.rgb(100, 200, 100), null, null, null));
        //aprHistoryPlot.addSeries(rollHistorySeries, new LineAndPointFormatter(Color.rgb(200, 100, 100), null, null, null));
        aprHistoryPlot.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
        aprHistoryPlot.setDomainStepValue(HISTORY_SIZE / 10);
        aprHistoryPlot.setTicksPerRangeLabel(3);
        aprHistoryPlot.setDomainLabel("Sample Index");
        aprHistoryPlot.getDomainLabelWidget().pack();
        aprHistoryPlot.setRangeLabel("Angle (Degs)");
        aprHistoryPlot.getRangeLabelWidget().pack();

        aprHistoryPlot.setRangeValueFormat(new DecimalFormat("#"));
        aprHistoryPlot.setDomainValueFormat(new DecimalFormat("#"));
        redrawer = new Redrawer(Arrays.asList(new Plot[]{aprHistoryPlot}), 100, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onPause() {
        redrawer.pause();
        super.onPause();
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        btnPreSetup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                View view = getView();
                if (null != view) {
                    mChatService.preSetupELM();
                }
            }
        });

        btnStartTelemetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View view = getView();
                if(view != null){
                    mChatService.beginTelemetry();
                }
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new UslugaBluetooth(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != UslugaBluetooth.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            mChatService.write(message);

            // Reset out string buffer to zero
            mOutStringBuffer.setLength(0);
        }
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case UslugaBluetooth.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            //TODO: Wyzerowanie liczników
                            break;
                        case UslugaBluetooth.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case UslugaBluetooth.STATE_LISTEN:
                        case UslugaBluetooth.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    //mConversationArrayAdapter.add("Ja: " + writeMessage);
                    //TODO: Zapisanie wysłanych danych do logów
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    //TODO: Zapis do logów
                    break;
                case Constants.RPM:
                    String sRpm = (String) msg.obj;
                    rpm = Float.valueOf(sRpm);
                    azimuthHistorySeries.addLast(null, rpm);
                    tvRpm.setText(sRpm);
                    //TODO: Zapis do logów
                    break;
                case Constants.LOAD:
                    String load = (String) msg.obj;
                    tvLoad.setText(load);
                    //TODO: Zapis do logów
                    break;
                case Constants.COOLANTTEMP:
                    String coolantTemp = (String) msg.obj;
                    tvCoolantTemp.setText(coolantTemp);
                    //TODO: Zapis do logów
                    break;
                case Constants.SPEED:
                    String speed = (String) msg.obj;
                    tvSpeed.setText(speed);
                    //TODO: Zapis do logów
                    break;
                case Constants.AIRTEMP:
                    String airTemp = (String) msg.obj;
                    tvAirTemp.setText(airTemp);
                    //TODO: Zapis do logów
                    break;
                case Constants.THROTTLE:
                    String throttle = (String) msg.obj;
                    tvThrottle.setText(throttle);
                    //TODO: Zapis do logów
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }
    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        redrawer.finish();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        redrawer.start();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == UslugaBluetooth.STATE_NONE) {
                // Start the Bluetooth chat services
                //mChatService.start(); //TODO Serwer?
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        btnPreSetup = (Button) view.findViewById(R.id.btnPreSetup);
        btnStartTelemetry = (Button) view.findViewById(R.id.btnStartTel);
        tvRpm = (TextView) view.findViewById(R.id.tvRpm);
        tvLoad = (TextView) view.findViewById(R.id.tvLoad);
        tvCoolantTemp = (TextView) view.findViewById(R.id.tvCoolantTepm);
        tvSpeed = (TextView) view.findViewById(R.id.tvSpeed);
        tvAirTemp = (TextView) view.findViewById(R.id.tvTempAirFlow);
        tvThrottle = (TextView) view.findViewById(R.id.tvThrottle);

        // setup the APR History plot:
        aprHistoryPlot = (XYPlot) view.findViewById(R.id.aprHistoryPlot);
    }
}
