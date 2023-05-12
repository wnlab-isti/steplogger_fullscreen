package it.cnr.isti.steplogger;

import android.app.Service;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import static java.lang.System.currentTimeMillis;

public class StepLoggerService extends Service {
    private static final String LOG_TAG = StepLoggerService.class.getName();

    private final String LOG_POSITION = "positions.log";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate()");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind()");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy()");
    }

    private final IStepLoggerService.Stub mBinder = new IStepLoggerService.Stub() {
        @Override
        public void logPosition(long timestamp, double x, double y, double z) throws RemoteException {
            Log.d(LOG_TAG, "Set logPosition: "+timestamp+", " + x + ", " + y + ", " + z);

            File dir = getDirLocation();

            if (dir != null) {
                // Save log
                try {
                    File fileToWrite = new File(dir, LOG_POSITION);
                    FileWriter fw = new FileWriter(fileToWrite, true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    bw.write(String.valueOf(currentTimeMillis()) + " " + x + " " + y + " " + z + "\n");
                    bw.close();
                    fw.close();
                    // Bugfix per rendere visibile il file di log in modalità USB
                    MediaScannerConnection.scanFile(getBaseContext(), new String[]{fileToWrite.getAbsolutePath()}
                            , null, null);

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(LOG_TAG, "error: " + e.getMessage());
                }
            }
        }
    };

    private File getDirLocation() {
        if(StepLoggerActivity.dirLocation.exists()){
            // Se esiste il file contenente la path dove scrivere i log
            // recupera il valore e lo ritorna ai due metodi
            try {
                BufferedReader br = new BufferedReader(new FileReader(StepLoggerActivity.dirLocation));
                String line = br.readLine();
                Log.d(LOG_TAG,"dir:"+line);
                br.close();
                return new File(line);
            }
            catch (IOException e) {
                //You'll need to add proper error handling here
                Log.e(LOG_TAG, "error: "+e.getMessage());
            }
            return null;
        }
        return null;
    }
}