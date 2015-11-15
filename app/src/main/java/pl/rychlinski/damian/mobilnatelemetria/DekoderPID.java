package pl.rychlinski.damian.mobilnatelemetria;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by damian on 13.11.15.
 */
public class DekoderPID extends Thread {
    private static final String TAG = "DekoderPID";

    private Handler parentHandler;
    private Queue<byte[]> incomingPIDsQue;

    private Handler myThreadHandler = new Handler(){
        public void handleMessage(Message msg) {
            if(msg.what == Constants.PID_MESSAGE){
                byte[] message = msg.getData().getByteArray("PID");
                incomingPIDsQue.add(message);
            }
        }
    };

    public DekoderPID(Handler parentHandler){
        Log.d(TAG, "create DecoderThread");
        this.parentHandler = parentHandler;
        incomingPIDsQue = new LinkedList<>();
    }

    @Override
    public void run() {
        while(!Thread.interrupted()){
            while(!incomingPIDsQue.isEmpty()){
                byte[] rawPID = incomingPIDsQue.poll();

                //TODO: dodać dekodowanie PIDOW
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public Handler getHandler() {
        return myThreadHandler;
    }
}
