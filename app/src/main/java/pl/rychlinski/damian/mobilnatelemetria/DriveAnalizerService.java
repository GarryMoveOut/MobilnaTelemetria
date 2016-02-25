package pl.rychlinski.damian.mobilnatelemetria;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

public class DriveAnalizerService extends Service implements SensorEventListener {
    private static final String TAG = "DriveAnalizerService";

    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    private float driveMark;

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
        w0 = 5;
        w1L = 1200;
        w1Pl = 1800;
        w1Pg = 3500;
        w2L = 80;
        w2P = 80;
        w3 = 50;
        w4 = 0.25f;
        w5 = 0; //?

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
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
        unregisterReceiver(receiver);
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
                if(rpm > 0f && rpm < 17000f) rpmChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.load")){
                Log.d(TAG,"Received Load");
                load = intent.getExtras().getFloat("LOAD");
                if(load > 0f && load < 100f) loadChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.coolanttemp")){
                Log.d(TAG,"Received Coolanttemp");
                coolanttemp = intent.getExtras().getInt("COOLANTTEMP");
                if(coolanttemp > -40 && coolanttemp < 215) coolantTempChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.speed")){
                Log.d(TAG,"Received Speed");
                speed = intent.getExtras().getInt("SPEED");
                if(speed > 0 && speed < 255) speedChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.airtemp")){
                Log.d(TAG,"Received Airspeed");
                airtemp = intent.getExtras().getInt("AIRTEMP");
                if(airtemp > -40 && airtemp < 215) airtempChk = true;
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.throttle")){
                Log.d(TAG,"Received throttle");
                throttle = intent.getExtras().getFloat("THROTTLE");
                if(throttle > 0f && throttle < 100f) throttleChk = true;
            }

            if(rpmChk && loadChk && coolantTempChk && speedChk && airtempChk && throttleChk){
                //TODO wczytać gforce
                if(speed > w0){
                    if(rpm < w1Pl && rpm > w1Pg){
                        if(coolanttemp < w2P){
                            driveMark =+ (rpm/100)*strtct; //kara x2
                        }else{
                            driveMark =- (rpm/100)*strtcf; //kara
                        }
                    }else{
                        driveMark =- 1 * strf; //nagroda
                    }
                }else{
                    if(rpm > w1L){
                        if(coolanttemp < w2L){
                            driveMark =+ (rpm/100)*sfrtct; //kara x2
                        }else{
                            driveMark =+ (rpm/100)*sfrtcf; //kara
                        }
                    }else{
                        driveMark =- 1*sfrf; //nagroda
                    }
                }

//czesc 2

                if(throttle > w3){
                    driveMark =+ throttle * tt; //kara
                }else{
                    driveMark =- (100 - throttle) * tf; //nagroda
                }

                if(gforce > w4){
                    driveMark =+ gforce * gt; //kara
                }else{
                    driveMark =- gforce * gf; //nagroda
                }

                if(load > w5){
                    //kara
                }else{
                    //nagroda
                }
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
                long diffTime = (curTime - lastUpdate);
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
}
