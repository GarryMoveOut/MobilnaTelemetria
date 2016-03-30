package pl.rychlinski.damian.mobilnatelemetria;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

public class DriveAnalizerService extends Service implements SensorEventListener {
    private static final String TAG = "DriveAnalizerService";

    private SensorManager senSensorManager;
    private Sensor senAccelerometer;
    private Queue<String> buffLog;
    private LoggerThread loggerThread;

    private int driveMark;

    //progi warunków
    int w0, w1L, w1Pl, w1Pg, w2L, w2P, w3, w5;
    float w4;

    //kary
    float sfrf, sfrtcf, sfrtct,
            strf, strtcf, strtct,
            tf, tt,
            gf, gt,
            lf,lt;

    private float gValY, gValZ;
    private float gforce;
    private long lastUpdate = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d("DriveAnalizerService", "onStartCommand");
        w0=5;
        w1L=1100;
        w1Pl = 1200;
        w1Pg = 2600;
        w2L = 80;
        w2P = 80;
        w3 = 50;
        w5 = 50;
        w4 = 1f;

        sfrf = intent.getFloatExtra("sfrf", 10f);
        sfrtcf = intent.getFloatExtra("sfrtcf",1);
        sfrtct = intent.getFloatExtra("sfrtct",2f);
        strf = intent.getFloatExtra("strf",20f);
        strtcf = intent.getFloatExtra("strtcf", 1f);
        strtct = intent.getFloatExtra("strtct",2f);
        tf = intent.getFloatExtra("tf",1f);
        tt = intent.getFloatExtra("tt",2f);
        gf = intent.getFloatExtra("gf",2f);
        gt = intent.getFloatExtra("gt",5f);
        lf = intent.getFloatExtra("lf",1f);
        lt = intent.getFloatExtra("lt",1f);


        buffLog = new LinkedList<>();
        loggerThread = new LoggerThread();
        loggerThread.start();

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d("DriveAnalizerService", "onCreate");
        driveMark = 0;

        IntentFilter filter = new IntentFilter();
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.rpm");
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.load");
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.coolanttemp");
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.speed");
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.airtemp");
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.throttle");
        registerReceiver(receiver, filter);

        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("DriveAnalizerService", "onDestroy");
        if(receiver != null) {
            unregisterReceiver(receiver);
        }
        senSensorManager.unregisterListener(this);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        boolean rpmChk, loadChk, coolantTempChk, speedChk, airtempChk, throttleChk;
        float rpm;
        float load;
        int coolanttemp;
        int speed;
        int airtemp;
        float throttle;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.rpm")){
                Log.d(TAG,"Received RPM");
                rpm = intent.getExtras().getFloat("RPM");
                if(rpm >= 0f && rpm <= 17000f) rpmChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.load")){
                Log.d(TAG,"Received Load");
                load = intent.getExtras().getFloat("LOAD");
                if(load >= 0f && load <= 100f) loadChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.coolanttemp")){
                Log.d(TAG,"Received Coolanttemp");
                coolanttemp = intent.getExtras().getInt("COOLANTTEMP");
                if(coolanttemp >= -40 && coolanttemp <= 215) coolantTempChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.speed")){
                Log.d(TAG,"Received Speed");
                speed = intent.getExtras().getInt("SPEED");
                if(speed >= 0 && speed <= 255) speedChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.airtemp")){
                Log.d(TAG,"Received Airspeed");
                airtemp = intent.getExtras().getInt("AIRTEMP");
                if(airtemp >= -40 && airtemp <= 215) airtempChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.throttle")){
                Log.d(TAG,"Received throttle");
                throttle = intent.getExtras().getFloat("THROTTLE");
                if(throttle >= 0f && throttle <= 100f) throttleChk = true;
            }

            if(rpmChk && loadChk && coolantTempChk && speedChk && airtempChk && throttleChk) {
                synchronized (this) { //TODO: Czy potrzebne?
                    if (speed > w0) {
                        if (rpm > w1Pl && rpm < w1Pg) {
                            driveMark -= 1 * strf; //nagroda
                        } else {
                            if (coolanttemp < w2P) {
                                driveMark += (rpm / 100) * strtct; //kara x2
                            } else {
                                driveMark += (rpm / 100) * strtcf; //kara
                            }
                        }
                    } else {
                        if (rpm > w1L) {
                            if (coolanttemp < w2L) {
                                driveMark += (rpm / 100) * sfrtct; //kara x2
                            } else {
                                driveMark += (rpm / 100) * sfrtcf; //kara
                            }
                        } else {
                            driveMark -= 1 * sfrf; //nagroda
                        }
                    }
                    //czesc 2
                    if (throttle > w3) {
                        driveMark += throttle * tt; //kara
                    } else {
                        driveMark -= (100 - throttle) * tf; //nagroda
                    }

                    if (gforce > w4) {
                        driveMark += gt * gforce; //kara
                    } else {
                        driveMark -= gforce * gf; //nagroda
                    }

                    if (load > w5) {
                        driveMark += load * lt; //kara
                    } else {
                        driveMark -= load * lf;  //nagroda
                    }

                    if(throttle < 15 && rpm <1000 && load > 20){
                        driveMark += 100; //dod. kara jazda na luzie
                    }
                    if(throttle <15 && load <20){
                        driveMark -= 50; //dod. nagroda hamowanie silnikiem
                    }

                    if(driveMark < 0){
                        driveMark = 0;
                    }

                    Intent driveMarkIntent = new Intent();
                    intent.setAction("pl.rychlinski.damian.mobilnatelemetria.driveanalizerservice.drivemark");
                    intent.putExtra("MARK", driveMark);
                    context.sendBroadcast(intent);

                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                    String currentDateandTime = sdf.format(new Date());

                    //zapisa do bufora
                    buffLog.offer("TIME: " + currentDateandTime +
                            " RPM: " + rpm +
                            " LOAD: "+ load +
                            " COOLANT: " + coolanttemp +
                            " SPEED: " + speed +
                            " AIRTEMP: " + airtemp +
                            " THROTTLE: " + throttle +
                            " Gy: "+ gValY +
                            " Gz: "+ gValZ +
                            " MARK: " + driveMark + "\n");
                    Log.d("DriverAnalizerService", "Data has been buffered");
                }
                rpmChk = false;
                loadChk = false;
                coolantTempChk = false;
                speedChk = false;
                airtempChk = false;
                throttleChk = false;
            }
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float gForceY = event.values[1];
            float gForceZ = event.values[2];

            long curTime = System.currentTimeMillis();

            if ((curTime - lastUpdate) > 100) {
                lastUpdate = curTime;

                gValY = Math.abs(gForceY);
                gValZ = Math.abs(gForceZ);
                gforce = gValY + gValZ;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private class LoggerThread extends Thread {
        private String filename = "driveLog.txt";
        private String filepath = "Logs";
        private ContextWrapper contextWrapper;
        private File myExternalFile;
        private File directory;

        public LoggerThread(){
            Log.d("LoggerThread", "Starting logger");
            contextWrapper = new ContextWrapper(getApplicationContext());
            directory = contextWrapper.getDir(filepath, Context.MODE_PRIVATE);

            if (!isExternalStorageAvailable() || isExternalStorageReadOnly()) {
                Log.e("LOGGERTGREAD","Brak dostępu do karty");
            }else {
                myExternalFile = new File(getExternalFilesDir(filepath), filename);
            }
        }

        @Override
        public void run() {
            super.run();

            Log.d("LoggerThread", "RUNING");
            while(!Thread.interrupted()){
                if(!buffLog.isEmpty()) {
                    try {
                        FileOutputStream fos = new FileOutputStream(myExternalFile, true);
                        fos.write(buffLog.poll().getBytes());
                        Log.d("LoggerThread", "Buffer has been saved");
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else{
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private boolean isExternalStorageReadOnly() {
            String extStorageState = Environment.getExternalStorageState();
            return Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState);
        }

        private boolean isExternalStorageAvailable() {
            String extStorageState = Environment.getExternalStorageState();
            return Environment.MEDIA_MOUNTED.equals(extStorageState);
        }
    }
}
