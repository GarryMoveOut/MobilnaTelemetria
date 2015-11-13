package pl.rychlinski.damian.mobilnatelemetria;

import android.os.Handler;
import android.os.Message;

/**
 * Created by damian on 13.11.15.
 */
public class DekoderPID extends Thread {
    private Handler parentHandler;

    private Handler myThreadHandler = new Handler(){
        public void handleMessage(Message msg) {
            // ...
        }
    };

    public DekoderPID(Handler parentHandler){
        this.parentHandler = parentHandler;
        // ...
    }

    @Override
    public void run() {
        // ...
    }

    public Handler getHandler() {
        return myThreadHandler;
    }
}
