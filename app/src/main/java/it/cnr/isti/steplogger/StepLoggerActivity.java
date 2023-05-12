package it.cnr.isti.steplogger;

import android.annotation.TargetApi;
import android.app.AlertDialog;


import android.content.DialogInterface;
import android.content.Intent;

import android.content.SharedPreferences;

import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;

import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;

import android.text.format.Time;

import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.System.currentTimeMillis;


public class StepLoggerActivity extends ActionBarActivity {

    private static final String LOG_TAG = StepLoggerActivity.class.getName();
    private Button counterButton;
    private int counterButtonVisibility = View.INVISIBLE;
    private String[] lines = null;
    private String folder = "";
    private String uid = "";
    private Integer index = 0;

    private boolean testMode = false;
    private boolean recreatedActivity;

    private boolean welcomeScreenDialogShowing = false;
    private boolean userIdDialogShowing = false;
    private boolean configDialogShowing = false;

    private File pathFiles = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS);
    private File dir = null;

    private AlertDialog alert;
    private EditText inputUID;
    private Config configuration;

    private static String LOG_STEPLOGGER = "buttonsPressed.log";

    static File dirLocation = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "it.cnr.isti.steplogger.dir");

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_steplogger);

        this.createEnvironmentPath();
        configuration = new Config();

        recreatedActivity = savedInstanceState != null;
        counterButton = (Button) findViewById(R.id.counterButton);

        if(recreatedActivity){
            // Se l'attività è ricreata recupero tutti i miei dati

            index = savedInstanceState.getInt("index");
            lines = savedInstanceState.getStringArray("lines");
            counterButtonVisibility = savedInstanceState.getInt("counterButtonVisibility");
            testMode = savedInstanceState.getBoolean("testMode");
            folder = savedInstanceState.getString("folder");
            uid = savedInstanceState.getString("uid");
            dir = new File(pathFiles, folder);
            userIdDialogShowing = savedInstanceState.getBoolean("userIdDialogShowing");
            welcomeScreenDialogShowing = savedInstanceState.getBoolean("welcomeScreenDialogShowing");
            testMode = savedInstanceState.getBoolean("testMode");
            configDialogShowing = savedInstanceState.getBoolean("configDialogShowing");

        } else {

            // Cancello per sicurezza il file contenente la path per il service
            removeDirLocation();

            try {
                // Provo a leggere il file di configurazione
                configuration.load();
                lines = configuration.get("counter").split(",");
            } catch (Exception e) {
                // Se ci sono errori avverto e chiudo l'applicazione
                configDialogShowing = true;
            }
        }

        counterButton.setVisibility(counterButtonVisibility);
        if(configDialogShowing) showErrorConfigDialog(configuration.getConfigurationFile());
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        if(lines != null && !configDialogShowing) {
            if (!recreatedActivity || (recreatedActivity && welcomeScreenDialogShowing))
                this.showWelcomeDialog();
            //parse la lines e prendi solo la label
            String buttonName  = lines[index].split(":")[0];
            counterButton.setText(buttonName);
            counterButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Registro l'informazione nel formato
                            // [timestamp] [etichetta]
                            String[] logButton  = lines[index].split(":");
                            if(saveLogFile(LOG_STEPLOGGER,
                                    getCurrentTimeStamp(false) + " : " + logButton[0]+ " : " + logButton[1] +" : "+ logButton[2]+" : "+logButton[3]+"\n",
                                    lines[index])){
                                // Quindi incremento il conatore
                                // e blocco i click sul bottone per 2 secondi
                                index++;
                                pauseButton();
                            }
                            if(index < lines.length) {
                                String buttonName  = lines[index].split(":")[0];
                                // Aggiorno il testo bottone
                                counterButton.setText(buttonName);
                            } else {
                                // Nascondo il bottone e salvo le misure nel file di log
                                counterButton.setVisibility(View.INVISIBLE);
                                resetCounter();
                                showToastMessage(getString(R.string.toast_stopMeasuring));
                                removeDirLocation();
                            }
                        }
                    }
            );
            if(userIdDialogShowing) startNewMeasuringSession();
        }
    }

    private void removeDirLocation() {
        dirLocation.delete();
        Log.d(LOG_TAG, dirLocation.toURI()+" deleted");
        scanFile(dirLocation);
    }

    private void showErrorConfigDialog(String filename) {
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title_warning))
                .setMessage(String.format(getString(R.string.warning_content_config_ini_file),
                        filename))
                .setPositiveButton(getString(R.string.warning_title_exit),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                dialog.dismiss();
                                configDialogShowing = false;
                                finish();
                            }
                        })
                .setCancelable(false)
                .show();
    }

    // Il bottone non deve essere subito ri-cliccabile
    private void pauseButton() {
        counterButton.setClickable(false);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                counterButton.setClickable(true);
            }
        }, 2000);

    }

    private void createEnvironmentPath() {
        File envPath = new File (pathFiles, this.getPackageName());
        if(!envPath.exists()){
            envPath.mkdir();
            Log.d(LOG_TAG, envPath.toURI()+" created");
        }
        pathFiles = envPath;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        testMode = prefs.getBoolean("test_mode", false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_steplogger, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if(lines !=null) {
            // Setto la visibilità dei pulsanti del menù
            if (counterButton.getVisibility() == View.VISIBLE) {
                menu.findItem(R.id.menu_action_newsession).setEnabled(false);
                menu.findItem(R.id.menu_action_settings).setEnabled(false);
            } else {
                menu.findItem(R.id.menu_action_newsession).setEnabled(true);
                menu.findItem(R.id.menu_action_settings).setEnabled(true);
            }
        } else {
            menu.findItem(R.id.menu_action_newsession).setVisible(false);
            menu.findItem(R.id.menu_action_settings).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(item.isEnabled()) {
            switch (id) {
                case R.id.menu_action_newsession:
                    this.startNewMeasuringSession();
                    break;

                case R.id.menu_action_settings:
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Se premo il tasto back chiedo conferma prima di uscire
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title_exit))
                .setMessage(String.format(getString(R.string.warning_content_exit_question)))
                .setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        removeDirLocation();
                        dialog.dismiss();
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    // Registro le variabili per la Persistenza dello stato dell'app
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("index", index);
        outState.putStringArray("lines", lines);
        outState.putInt("counterButtonVisibility", counterButton.getVisibility());
        outState.putBoolean("testMode", testMode);
        outState.putString("folder", folder);
        outState.putString("uid", userIdDialogShowing ? inputUID.getText().toString() : uid);
        outState.putBoolean("welcomeScreenDialogShowing", welcomeScreenDialogShowing);
        outState.putBoolean("userIdDialogShowing", userIdDialogShowing);
        outState.putBoolean("configDialogShowing", configDialogShowing);

        if(alert != null && alert.isShowing()) alert.dismiss();

        super.onSaveInstanceState(outState);
    }

    private static String getCurrentTimeStamp(Boolean isFolderLabel){
        Time now = new Time();
        now.setToNow();
        return "" + (isFolderLabel ? now.format("%Y%m%dT%H%M%S") : currentTimeMillis());
    }

    private void showWelcomeDialog() {
        welcomeScreenDialogShowing = true;
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.welcomedialog_title))
                .setMessage(getString(R.string.welcomedialog_text))
                .setNeutralButton(R.string.menu_action_newsession, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        welcomeScreenDialogShowing = false;
                        startNewMeasuringSession();
                        dialog.dismiss();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        welcomeScreenDialogShowing = false;
                        dialog.dismiss();
                    }
                })
                .show();
    }


    private void startNewMeasuringSession() {
        // Resetto il contatore
        this.resetCounter();

        inputUID = new EditText(this);
        inputUID.setSingleLine();
        inputUID.setBackgroundColor(Color.WHITE);
        inputUID.setTextColor(Color.BLACK);
        inputUID.append(uid);

        userIdDialogShowing = true;
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.uid_title))
                .setMessage(getString(R.string.uid_msg))
                .setView(inputUID)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
                    public void onClick(DialogInterface dialog, int whichButton) {
                        uid = inputUID.getText().toString();

                        if (!uid.isEmpty()) {

                            if (!testMode) {
                                // Creo la cartella se non sono in modalità test
                                folder = getCurrentTimeStamp(true) +
                                        (uid.length() > 0 ? "_" + uid : "");
                                dir = new File(pathFiles, folder);
                                if (!dir.exists()) {
                                    dir.mkdir();
                                }
                                updateDirLocation();
                            }


                            // Rendo il pulsante del contatore visibile e cliccabile
                            counterButton.setVisibility(View.VISIBLE);

                            // Avverto che la campagna di misure è iniziata
                            String toastMsg = getString(R.string.toast_startMeasuring);

                            if (uid.length() > 0)
                                toastMsg += String.format("\n" + getString(
                                                R.string.toast_userMeasuring),
                                        uid);
                            if (testMode)
                                toastMsg += "\n" + getString(R.string.toast_testmode);

                            showToastMessage(toastMsg);

                            userIdDialogShowing = false;
                            dialog.dismiss();
                        }
                    }



                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        userIdDialogShowing = false;
                        dialog.dismiss();
                        return;
                    }
                })
                .show();



    }

    private void updateDirLocation() {
        // Creo il file dove scrivo la path della directory dove il Service andrà
        // a scrivere gli altri log
        try {
            dirLocation.createNewFile();
            FileWriter fw = new FileWriter(dirLocation, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(dir.toString());
            bw.close();
            fw.close();

            // Bugfix per rendere visibile i file in modalità USB
            scanFile(dirLocation);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean saveLogFile(String filename, String content, String label){
        if(!testMode) {

            // Se la modalità test non è attiva salvo le misure nel file di log
            File fileToWrite = new File(dir, filename);

            try {
                FileWriter fw = new FileWriter(fileToWrite, true);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(content);
                bw.close();
                fw.close();
                Log.d(LOG_TAG, fileToWrite.toURI()+" written");

                // Bugfix per rendere visibile i file in modalità USB
                scanFile(fileToWrite);

            } catch (Exception e) {
                showToastMessage(getString(R.string.toast_notSaved));
                Log.e(LOG_TAG, fileToWrite.toURI() + " not written!\n" + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private void scanFile(File  permFile){
        Intent mediaScannerIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri fileContentUri = Uri.fromFile(permFile); // With 'permFile' being the File object
        mediaScannerIntent.setData(fileContentUri);
        this.sendBroadcast(mediaScannerIntent);
    }

    private void showToastMessage(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        LinearLayout OurLayout = (LinearLayout) toast.getView();

        if (OurLayout.getChildCount() > 0) {
            TextView SampleView = (TextView) OurLayout.getChildAt(0);
            SampleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        }
        toast.show();
    }

    private void resetCounter() {
        index = 0;
        String buttonName  = lines[index].split(":")[0];

        counterButton.setText(buttonName);
    }
}