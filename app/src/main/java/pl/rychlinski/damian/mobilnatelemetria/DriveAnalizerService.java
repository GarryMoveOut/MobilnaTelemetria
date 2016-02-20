package pl.rychlinski.damian.mobilnatelemetria;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class DriveAnalizerService extends Service {
    private static final String TAG = "DriveAnalizerService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.rpm");
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.load");
        filter.addAction("pl.rychlinski.damian.mobilnatelemetria.pid.coolanttemp");
        registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.rpm")){
                Log.d(TAG,"Received RPM");
                float rpm = intent.getExtras().getFloat("RPM");
                float tmp = rpm - 1f; //TODO: Do usunięcia po skączeniu testów
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.load")){
                Log.d(TAG,"Received Load");
                float load = intent.getExtras().getFloat("LOAD");
                float tmp = load - 1f; //TODO: Do usunięcia po skończeniu testów
            }

            if(action.equals("pl.rychlinski.damian.mobilnatelemetria.pid.coolanttemp")){
                Log.d(TAG,"Received Coolanttemp");
                float coolanttemp = intent.getExtras().getInt("COOLANTTEMP");
                float tmp = coolanttemp - 1f; //TODO: Do usunięcia po skończeniu testów
            }

        }
    };
}
