package pl.rychlinski.damian.mobilnatelemetria;


import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


/**
 * A simple {@link Fragment} subclass.
 */
public class BluetoothFragmentv2 extends Fragment {
    private static final String TAG = "BluetoothFragment v2";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    private BluetoothAdapter mBluetoothAdapter = null;
    private UslugaBluetooth mChatService = null;
    private StringBuffer mOutStringBuffer;
    private String mConnectedDeviceName = null;

    private TextView tvOcena, tvAirTemp, tvCoolantTemp;

    private LineChart cRPM;
    private LineData RPMdata;
    private LineDataSet rpmSet;

    private LineChart cSpeed;
    private LineData speedData;
    private LineDataSet speedSet;

    private LineChart cLoad;
    private LineData loadData;
    private LineDataSet loadSet;

    private LineChart cThrottle;
    private LineData throttleData;
    private LineDataSet throttleSet;

    private float sfrf, sfrtcf, sfrtct,
            strf, strtcf, strtct,
            tf, tt,
            gf, gt,
            lf,lt;

    public BluetoothFragmentv2() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Activity activity = getActivity();
            Toast.makeText(activity, "Brak Bluetooth", Toast.LENGTH_LONG).show();
            activity.finish();
        }

        sfrf = 10f;
        sfrtcf = 1f;
        sfrtct = 2f;
        strf = 20f;
        strtcf = 1f;
        strtct = 2f;
        tf = 1f;
        tt = 2f;
        gf = 2f;
        gt = 5f;
        lf = 1f; //TODO: dobrać współczynnik
        lt = 1f;

        Log.d(TAG, "Wysłanie intencji do DriveAnalizerService");
        Intent intent = new Intent(getActivity(), DriveAnalizerService.class);
        intent.putExtra("sfrf",sfrf);
        intent.putExtra("sfrtcf", sfrtcf);
        intent.putExtra("sfrtct",sfrtct);
        intent.putExtra("strf",strf);
        intent.putExtra("strtcf",strtcf);
        intent.putExtra("strtct",strtct);
        intent.putExtra("tf",tf);
        intent.putExtra("tt",tt);
        intent.putExtra("gf",gf);
        intent.putExtra("gt",gt);
        intent.putExtra("lf",lf);
        intent.putExtra("lt",lt);

        getActivity().startService(intent);

        IntentFilter filter = new IntentFilter();
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.driveanalizerservice.drivemark");
        getActivity().registerReceiver(receiver, filter);
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

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

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
            Activity activity = getActivity();

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String currentDateandTime = sdf.format(new Date());

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
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);

                    break;
                case Constants.RPM:
                    String sRpm = (String) msg.obj;
                    sRpm = sRpm.replace(",",".");
                    float fRpm = Float.valueOf(sRpm);

                    RPMdata.addXValue(currentDateandTime);
                    RPMdata.addEntry(new Entry(fRpm, rpmSet.getEntryCount()), 0);

                    // let the chart know it's data has changed
                    cRPM.notifyDataSetChanged();

                    // limit the number of visible entries
                    cRPM.setVisibleXRangeMaximum(120);
                    // mChart.setVisibleYRange(30, AxisDependency.LEFT);

                    // move to the latest entry
                    cRPM.moveViewToX(RPMdata.getXValCount() - 121);

                    // this automatically refreshes the chart (calls invalidate())
                    // mChart.moveViewTo(data.getXValCount()-7, 55f,
                    // AxisDependency.LEFT);

                    break;
                case Constants.LOAD:
                    String load = (String) msg.obj;
                    load = load.replace(",",".");
                    float fLoad = Float.valueOf(load);
                    loadData.addXValue(currentDateandTime);
                    loadData.addEntry(new Entry(fLoad, loadSet.getEntryCount()), 0);

                    // let the chart know it's data has changed
                    cLoad.notifyDataSetChanged();

                    // limit the number of visible entries
                    cLoad.setVisibleXRangeMaximum(120);
                    // mChart.setVisibleYRange(30, AxisDependency.LEFT);

                    // move to the latest entry
                    cLoad.moveViewToX(loadData.getXValCount() - 121);

                    // this automatically refreshes the chart (calls invalidate())
                    // mChart.moveViewTo(data.getXValCount()-7, 55f,
                    // AxisDependency.LEFT);

                    break;
                case Constants.COOLANTTEMP:
                    String coolantTemp = (String) msg.obj;
                    tvCoolantTemp.setText(coolantTemp);

                    break;
                case Constants.SPEED:
                    String speed = (String) msg.obj;
                    int iSpeed = Integer.valueOf(speed);

                    speedData.addXValue(currentDateandTime);
                    speedData.addEntry(new Entry(iSpeed, speedSet.getEntryCount()), 0);

                    // let the chart know it's data has changed
                    cSpeed.notifyDataSetChanged();

                    // limit the number of visible entries
                    cSpeed.setVisibleXRangeMaximum(120);
                    // mChart.setVisibleYRange(30, AxisDependency.LEFT);

                    // move to the latest entry
                    cSpeed.moveViewToX(speedData.getXValCount() - 121);

                    // this automatically refreshes the chart (calls invalidate())
                    // mChart.moveViewTo(data.getXValCount()-7, 55f,
                    // AxisDependency.LEFT);

                    break;
                case Constants.AIRTEMP:
                    String airTemp = (String) msg.obj;
                    tvAirTemp.setText(airTemp);

                    break;
                case Constants.THROTTLE:
                    String throttle = (String) msg.obj;
                    throttle = throttle.replace(",",".");
                    float fThrottle = Float.valueOf(throttle);
                    throttleData.addXValue(currentDateandTime);
                    throttleData.addEntry(new Entry(fThrottle, throttleSet.getEntryCount()), 0);

                    // let the chart know it's data has changed
                    cThrottle.notifyDataSetChanged();

                    // limit the number of visible entries
                    cThrottle.setVisibleXRangeMaximum(120);
                    // mChart.setVisibleYRange(30, AxisDependency.LEFT);

                    // move to the latest entry
                    cThrottle.moveViewToX(throttleData.getXValCount() - 121);

                    // this automatically refreshes the chart (calls invalidate())
                    // mChart.moveViewTo(data.getXValCount()-7, 55f,
                    // AxisDependency.LEFT);

                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Połączono z "
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
        Activity activity = getActivity();
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
        Activity activity = getActivity();
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
                    Log.d(TAG, "BT nie włączony");
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetoothv2, container, false);

        //chartList = (ListView) view.findViewById(R.id.chartList);
        tvOcena = (TextView) view.findViewById(R.id.tvOcena);
        tvAirTemp = (TextView) view.findViewById(R.id.tvAirTemp);
        tvCoolantTemp = (TextView) view.findViewById(R.id.tvCoolantTepm);

        cRPM = (LineChart) view.findViewById(R.id.lcRpm);
        cSpeed = (LineChart) view.findViewById(R.id.lcSpeed);
        cLoad = (LineChart) view.findViewById(R.id.lcLoadThrottle);
        cThrottle = (LineChart) view.findViewById(R.id.lcLoad);

        setupLineChartRpm();
        setupLineChartSpeed();
        setupLineChartLoad();
        setupLineChartThrottle();

        return view;
    }

    private void setupLineChartThrottle() {
        // no description text
        cThrottle.setDescription("");
        cThrottle.setNoDataTextDescription("Brak danych");

        // enable touch gestures
        cThrottle.setTouchEnabled(true);

        // enable scaling and dragging
        cThrottle.setDragEnabled(true);
        cThrottle.setScaleEnabled(true);
        cThrottle.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        cThrottle.setPinchZoom(true);

        // set an alternative background color
        cThrottle.setBackgroundColor(Color.TRANSPARENT);

        throttleData = new LineData();
        throttleData.setValueTextColor(Color.BLACK);

        // add empty data
        cThrottle.setData(throttleData);

        //Typeface tf = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");

        // get the legend (only possible after setting data)
        Legend l = cThrottle.getLegend();

        // modify the legend ...
        // l.setPosition(LegendPosition.LEFT_OF_CHART);
        l.setForm(Legend.LegendForm.LINE);
        //l.setTypeface(tf);
        l.setTextColor(Color.BLACK);

        XAxis xl = cThrottle.getXAxis();
        //xl.setTypeface(tf);
        xl.setTextColor(Color.BLACK);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = cThrottle.getAxisLeft();
        //leftAxis.setTypeface(tf);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaxValue(100f);
        leftAxis.setAxisMinValue(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = cThrottle.getAxisRight();
        rightAxis.setEnabled(false);

        throttleSet = new LineDataSet(null, "Przepustnica [%]");
        throttleSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        throttleSet.setColor(Color.GREEN);
        throttleSet.setLineWidth(2f);
        throttleSet.setDrawCircles(false);
        throttleSet.setFillAlpha(65);
        throttleSet.setFillColor(ColorTemplate.getHoloBlue());
        throttleSet.setHighLightColor(Color.rgb(244, 117, 117));
        throttleSet.setValueTextColor(Color.WHITE);
        throttleSet.setValueTextSize(9f);
        throttleSet.setDrawValues(false);

        throttleData.addDataSet(throttleSet);
    }

    private void setupLineChartLoad() {
        // no description text
        cLoad.setDescription("");
        cLoad.setNoDataTextDescription("You need to provide data for the chart.");

        // enable touch gestures
        cLoad.setTouchEnabled(true);

        // enable scaling and dragging
        cLoad.setDragEnabled(true);
        cLoad.setScaleEnabled(true);
        cLoad.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        cLoad.setPinchZoom(true);

        // set an alternative background color
        cLoad.setBackgroundColor(Color.TRANSPARENT);

        loadData = new LineData();
        loadData.setValueTextColor(Color.BLACK);

        // add empty data
        cLoad.setData(loadData);

        //Typeface tf = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");

        // get the legend (only possible after setting data)
        Legend l = cLoad.getLegend();

        // modify the legend ...
        // l.setPosition(LegendPosition.LEFT_OF_CHART);
        l.setForm(Legend.LegendForm.LINE);
        //l.setTypeface(tf);
        l.setTextColor(Color.BLACK);

        XAxis xl = cLoad.getXAxis();
        //xl.setTypeface(tf);
        xl.setTextColor(Color.BLACK);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = cLoad.getAxisLeft();
        //leftAxis.setTypeface(tf);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaxValue(100f);
        leftAxis.setAxisMinValue(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = cLoad.getAxisRight();
        rightAxis.setEnabled(false);

        loadSet = new LineDataSet(null, "Obciążenie [%]");
        loadSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        loadSet.setColor(Color.YELLOW);
        loadSet.setDrawCircles(false);
        loadSet.setLineWidth(2f);
        loadSet.setFillAlpha(65);
        loadSet.setFillColor(ColorTemplate.getHoloBlue());
        loadSet.setHighLightColor(Color.rgb(244, 117, 117));
        loadSet.setValueTextColor(Color.BLACK);
        loadSet.setValueTextSize(9f);
        loadSet.setDrawValues(false);

        loadData.addDataSet(loadSet);
    }

    private void setupLineChartSpeed() {
        // no description text
        cSpeed.setDescription("");
        cSpeed.setNoDataTextDescription("You need to provide data for the chart.");

        // enable touch gestures
        cSpeed.setTouchEnabled(true);

        // enable scaling and dragging
        cSpeed.setDragEnabled(true);
        cSpeed.setScaleEnabled(true);
        cSpeed.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        cSpeed.setPinchZoom(true);

        // set an alternative background color
        cSpeed.setBackgroundColor(Color.TRANSPARENT);

        speedData = new LineData();
        speedData.setValueTextColor(Color.BLACK);

        // add empty data
        cSpeed.setData(speedData);

        //Typeface tf = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");

        // get the legend (only possible after setting data)
        Legend l = cSpeed.getLegend();

        // modify the legend ...
        // l.setPosition(LegendPosition.LEFT_OF_CHART);
        l.setForm(Legend.LegendForm.LINE);
        //l.setTypeface(tf);
        l.setTextColor(Color.BLACK);

        XAxis xl = cSpeed.getXAxis();
        //xl.setTypeface(tf);
        xl.setTextColor(Color.BLACK);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = cSpeed.getAxisLeft();
        //leftAxis.setTypeface(tf);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaxValue(150f);
        leftAxis.setAxisMinValue(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = cSpeed.getAxisRight();
        rightAxis.setEnabled(false);

        speedSet = new LineDataSet(null, "km/h");
        speedSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        speedSet.setColor(Color.CYAN);
        speedSet.setDrawCircles(false);
        speedSet.setLineWidth(2f);
        speedSet.setFillAlpha(65);
        speedSet.setFillColor(ColorTemplate.getHoloBlue());
        speedSet.setHighLightColor(Color.rgb(244, 117, 117));
        speedSet.setValueTextColor(Color.BLACK);
        speedSet.setValueTextSize(9f);
        speedSet.setDrawValues(false);

        speedData.addDataSet(speedSet);
    }

    private void setupLineChartRpm(){
        // no description text
        cRPM.setDescription("");
        cRPM.setNoDataTextDescription("You need to provide data for the chart.");

        // enable touch gestures
        cRPM.setTouchEnabled(true);

        // enable scaling and dragging
        cRPM.setDragEnabled(true);
        cRPM.setScaleEnabled(true);
        cRPM.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        cRPM.setPinchZoom(true);

        // set an alternative background color
        cRPM.setBackgroundColor(Color.TRANSPARENT);

        RPMdata = new LineData();
        RPMdata.setValueTextColor(Color.BLACK);

        // add empty data
        cRPM.setData(RPMdata);

        //Typeface tf = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");

        // get the legend (only possible after setting data)
        Legend l = cRPM.getLegend();

        // modify the legend ...
        // l.setPosition(LegendPosition.LEFT_OF_CHART);
        l.setForm(Legend.LegendForm.LINE);
        //l.setTypeface(tf);
        l.setTextColor(Color.BLACK);

        XAxis xl = cRPM.getXAxis();
        //xl.setTypeface(tf);
        xl.setTextColor(Color.BLACK);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = cRPM.getAxisLeft();
        //leftAxis.setTypeface(tf);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaxValue(5000f);
        leftAxis.setAxisMinValue(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = cRPM.getAxisRight();
        rightAxis.setEnabled(false);

        rpmSet = new LineDataSet(null, "Obroty/min");
        rpmSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        rpmSet.setColor(ColorTemplate.getHoloBlue());
        rpmSet.setDrawCircles(false);
        rpmSet.setLineWidth(2f);
        rpmSet.setFillAlpha(65);
        rpmSet.setFillColor(ColorTemplate.getHoloBlue());
        rpmSet.setHighLightColor(Color.rgb(244, 117, 117));
        rpmSet.setValueTextColor(Color.BLACK);
        rpmSet.setValueTextSize(9f);
        rpmSet.setDrawValues(false);

        RPMdata.addDataSet(rpmSet);
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
            case R.id.preSetup:{
                View view = getView();
                if (null != view) {
                    mChatService.preSetupELM();
                }
                return true;
            }
            case R.id.startTel:{
                View view = getView();
                if(view != null){
                    mChatService.beginTelemetry();
                }
                return true;
            }
            case R.id.marksWeight:{
                LayoutInflater li = LayoutInflater.from(getActivity());
                View promptsView = li.inflate(R.layout.marks_dialog, null);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                        getActivity());

                alertDialogBuilder.setView(promptsView);

                final EditText etSfrf = (EditText) promptsView.findViewById(R.id.etSfrf);
                final EditText etSfrtcf = (EditText) promptsView.findViewById(R.id.etSfrtcf);
                final EditText etSfrtct = (EditText) promptsView.findViewById(R.id.etSfrtct);
                final EditText etStrf = (EditText) promptsView.findViewById(R.id.etStrf);
                final EditText etStrtcf = (EditText) promptsView.findViewById(R.id.etStrtcf);
                final EditText etStrtct = (EditText) promptsView.findViewById(R.id.etStrtct);
                final EditText etTt = (EditText) promptsView.findViewById(R.id.ett);
                final EditText etTf = (EditText) promptsView.findViewById(R.id.etTf);
                final EditText etGf = (EditText) promptsView.findViewById(R.id.etGf);
                final EditText etGt = (EditText) promptsView.findViewById(R.id.etGt);
                final EditText etLf = (EditText) promptsView.findViewById(R.id.etlf);
                final EditText etLt = (EditText) promptsView.findViewById(R.id.etlt);

                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int id) {
                                        //Zatwierdzenie zmiany
                                        getActivity().stopService(new Intent(getActivity(), DriveAnalizerService.class));

                                        sfrf = Float.valueOf(etSfrf.getText().toString());
                                        sfrtcf = Float.valueOf(etSfrtcf.getText().toString());
                                        sfrtct = Float.valueOf(etSfrtct.getText().toString());
                                        strf = Float.valueOf(etStrf.getText().toString());
                                        strtcf = Float.valueOf(etStrtcf.getText().toString());
                                        strtct = Float.valueOf(etStrtct.getText().toString());
                                        tt = Float.valueOf(etTt.getText().toString());
                                        tf = Float.valueOf(etTf.getText().toString());
                                        gf = Float.valueOf(etGf.getText().toString());
                                        gt = Float.valueOf(etGt.getText().toString());
                                        lf = Float.valueOf(etLf.getText().toString());
                                        lt = Float.valueOf(etLt.getText().toString());

                                        Intent intent = new Intent(getActivity(), DriveAnalizerService.class);
                                        intent.putExtra("sfrf",sfrf);
                                        intent.putExtra("sfrtcf", sfrtcf);
                                        intent.putExtra("sfrtct",sfrtct);
                                        intent.putExtra("strf",strf);
                                        intent.putExtra("strtcf",strtcf);
                                        intent.putExtra("strtct",strtct);
                                        intent.putExtra("tf",tf);
                                        intent.putExtra("tt",tt);
                                        intent.putExtra("gf",gf);
                                        intent.putExtra("gt",gt);
                                        intent.putExtra("lf",lf);
                                        intent.putExtra("lt",lt);

                                        getActivity().startService(intent);
                                    }
                                })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int id) {
                                        dialog.cancel();
                                    }
                                });
                // create alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();

                // show it
                alertDialog.show();
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
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(receiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
        //wyrejestrowanie filtru usługi
        //if(receiver != null) {
        //    getActivity().unregisterReceiver(receiver);
        //}
        getActivity().stopService(new Intent(getActivity(), DriveAnalizerService.class));
    }

    @Override
    public void onResume() {
        super.onResume();

        //Rejestracja filtru odbierającego wiadomości od usługi analizatora jazdy
        IntentFilter filter = new IntentFilter();
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.driveanalizerservice.drivemark");
        getActivity().registerReceiver(receiver, filter);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.driveanalizerservice.drivemark")){
                int ocena = intent.getExtras().getInt("MARK");
                String sOcena = String.valueOf(ocena);
                tvOcena.setText(sOcena);
            }
        }
    };
}
