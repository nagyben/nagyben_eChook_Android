package com.ben.echookcompanion;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import com.ben.echookcompanion.echookcompanion.R;
import com.ben.echookcompanion.fragments.FourGraphsBars;
import com.ben.echookcompanion.fragments.LapHistoryFragment;
import com.ben.echookcompanion.fragments.RaceMapFragment;
import com.ben.echookcompanion.fragments.SettingsFragment;
import com.ben.echookcompanion.fragments.SixGraphsBars;
import com.ben.echookcompanion.threads.BTDataParser;
import com.ben.echookcompanion.threads.BTStreamReader;
import com.ben.echookcompanion.threads.DataToCsvFile;
import com.ben.echookcompanion.threads.RandomGenerator;
import com.ben.echookcompanion.threads.UDPSender;
import com.ben.echookcompanion.util.Accelerometer;
import com.ben.echookcompanion.util.BluetoothManager;
import com.ben.echookcompanion.util.CyclingArrayList;
import com.ben.echookcompanion.util.DrivenLocation;
import com.ben.echookcompanion.util.DrivenSettings;
import com.ben.echookcompanion.util.GraphData;
import com.ben.echookcompanion.util.NetworkMonitor;
import com.ben.echookcompanion.util.UpdateFragment;

import java.io.File;
import java.util.Objects;
import java.util.Timer;


public class MainActivity
        extends AppCompatActivity
        implements	BTDataParser.BTDataParserListener,
        BluetoothManager.BluetoothEvents,
        SettingsFragment.SettingsInterface {

    public static TextView myMode;

    public static TextView myDataFileSize;
    public static TextView myDataFileName;

    public static TextView myBTCarName;

    public static TextView myBTState;
    public static TextView myLogging;

    public static Chronometer LapTimer;
    public static TextView prevLapTime;
    public static TextView LapNumber;

    public static RandomGenerator mRandomGenerator = new RandomGenerator();
    public BTDataParser mBTDataParser = new BTDataParser(this); // can't be static because of (this)
    public static DataToCsvFile mDataToCSVFile = new DataToCsvFile();
    public static BTStreamReader mBTStreamReader; // initialize below
    public static UDPSender mUDPSender; // initialize below
    public static NetworkMonitor mNetworkMonitor = new NetworkMonitor();

    private static Timer UIUpdateTimer; // don't initialize because it should be done below

    public static final Handler MainActivityHandler = new Handler();

    public static DrivenLocation myDrivenLocation; // must be initialized below or else null object ref error

    public static Accelerometer myAccelerometer;

    private static Context context;
    public static final BluetoothManager myBluetoothManager = new BluetoothManager();

    private static final CyclingArrayList<UpdateFragment> FragmentList = new CyclingArrayList<>();
    public static UpdateFragment currentFragment;
    private static View SnackbarPosition;

	/* ========= */
	/* LIFECYCLE */
    /* ========= */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();

        setContentView(R.layout.activity_main_v2);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

        myMode = (TextView) findViewById(R.id.txt_Mode);

        myDataFileName = (TextView) findViewById(R.id.txtDataFileName);
        myDataFileSize = (TextView) findViewById(R.id.txtDataFileSize);

        myBTCarName = (TextView) findViewById(R.id.txtBTCarName);

        myBTState = (TextView) findViewById(R.id.txtBTState);
        myLogging = (TextView) findViewById(R.id.txtLogging);

        LapTimer = (Chronometer) findViewById(R.id.LapTimer);
        prevLapTime = (TextView) findViewById(R.id.previousLapTime);
        LapNumber = (TextView) findViewById(R.id.lap);

        SnackbarPosition = findViewById(R.id.snackbarPosition);

        MainActivity.myLogging.setText("NO");
        MainActivity.myLogging.setTextColor(getResources().getColor(R.color.negative));

        //StartUIUpdater();

        DrivenSettings.InitializeSettings();

        myDrivenLocation = new DrivenLocation(); // must be initialized here or else null object ref error

        GraphData.InitializeGraphDataSets();

        InitializeLongClickStart();

        InitializeFragmentList();
        CycleView();

        StartDataParser();

        RequestAllPermissions();

        UpdateBTStatus();
        UpdateBTCarName();
    }

    @Override
    protected void onStart() {
        super.onStart();
        myDrivenLocation.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            myDrivenLocation.disconnect();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        //StopUIUpdater();
        ForceStop(myDataFileName);
        myMode			= null;
        myDataFileName	= null;
        myDataFileSize	= null;
        myBTCarName		= null;
        myBTState		= null;
        myLogging		= null;
        LapTimer		= null;
        prevLapTime		= null;
        LapNumber		= null;


        myAccelerometer = null;
        myDrivenLocation = null;
        myBluetoothManager.unregisterListeners();

        FragmentList.clear();

        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            this.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void RequestAllPermissions() {
        String[] permissions = new String[] {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
        };

        boolean permissionRequestRequired = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionRequestRequired = true;
            }
        }

        if (permissionRequestRequired) {
            ActivityCompat.requestPermissions(this, permissions, Global.PERMISSIONS_REQUEST);
        } else {
            InitializeAllTheThings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == Global.PERMISSIONS_REQUEST) {
            boolean allPermissionsGranted = true;

            // check grantResult
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            // if a permission was not granted, ask user to do it again
            // TODO implement recursive permissions loop with dialog box

            if (allPermissionsGranted) {
                // thanks bro, initialize all the things
                InitializeAllTheThings();
            } else {
                // douche. now you can't play.
                System.exit(0);
            }
        } else {
            showMessage("Weird permissions requestCode received: " + requestCode);
        }
    }

    private void InitializeAllTheThings() {
        StartUDPSender();

        myAccelerometer = new Accelerometer((SensorManager) getSystemService(Context.SENSOR_SERVICE));

        UpdateDataFileInfo();

        myBluetoothManager.setBluetoothEventsListener(this);
    }

    private void InitializeFragmentList() {
        FragmentList.add(new RaceMapFragment());
        FragmentList.add(new SixGraphsBars());
        FragmentList.add(new FourGraphsBars());
        FragmentList.add(new LapHistoryFragment());
    }

    private void InitializeLongClickStart() {
        // We can't do this in XML so must do it programatically
        FloatingActionButton startButton = (FloatingActionButton) findViewById(R.id.start);
        startButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                StartWithForcedLogging(v);
                return true;
            }
        });
    }

	/* ======= */
	/* TOASTER */
    /* ======= */

    public static void showMessage(String theMsg) {
        showMessage(theMsg, Toast.LENGTH_SHORT);
    }

    public static void showMessage(String string, int length) {
        /*
		final Toast msg = Toast.makeText(context, string, Toast.LENGTH_LONG);
		msg.show();
		*/
        showSnackbar(string);
    }

    public static void showError(Exception e) {
        showMessage(e.getMessage(), Toast.LENGTH_SHORT);
    }

    public static void showSnackbar(String msg, int length) {
        try {
            Snackbar
                    .make(SnackbarPosition, msg, length)
                    //.setActionTextColor(R.color.negative)
                    .show();
        } catch (Exception ignored) {}
    }

    public static void showSnackbar(String msg) {
        showSnackbar(msg, Snackbar.LENGTH_SHORT);
    }

	/* ================ */
	/* BUTTON LISTENERS */
    /* ================ */

    public void OpenBT(View v) {
        if (!Objects.equals(Global.BTDeviceName, "")) { // fucking Java string comparators...
            try {
                myBluetoothManager.findBT();
                myBluetoothManager.openBT(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            showMessage("Error: Bluetooth device name not given in Settings. Please go to Settings and enter the device name");
        }
    }

    public void CloseBT(View v) {
        try {
            StopStreamReader();
            myBluetoothManager.closeBT();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Start(View v) {
        try {
            if (Global.Mode == Global.MODE.DEMO) {
                StartDemoMode();
            } else if (Global.Mode == Global.MODE.RACE) {
                StartRaceMode();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Stop(View v) {
        try {
            StopRandomGenerator();
            StopDataLogger();

            UpdateDataFileInfo();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void StartWithForcedLogging(View v) {
        try {
            if (Global.Mode == Global.MODE.DEMO) {
                StartDataLogger();
                StartDemoMode();
            } else if (Global.Mode == Global.MODE.RACE) {
                StartRaceMode();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** For testing purposes only. Stops all the threads immediately. Called when the user double-taps the data file name in the top-left corner of the app*/
    public void ForceStop(View v) {
        try {
            StopRandomGenerator();
        } catch (Exception ignored) {}
        try {
            StopDataLogger();
        } catch (Exception ignored) {}
        try {
            StopStreamReader();
        } catch (Exception ignored) {}
            UpdateDataFileInfo();
        try {
            UIUpdateTimer.purge();
        } catch (Exception ignored) {}
    }

    /** Called when the user taps the cogwheel in the app. Launches the settings fragment */
    public void LaunchSettings(View v) {
        SettingsFragment settingsFragment = new SettingsFragment();
        settingsFragment.setSettingsListener(this);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.overlay, settingsFragment)
                .addToBackStack(null)
                .commit();
    }

    /** Called when the user taps "cycle" in the app. Cycles the view between the fragments contained in FragmentList */
    public void Cycle(View v) {
        byte[] cyclepacket = new byte[5];
        cyclepacket[0] = Global.STARTBYTE;
        cyclepacket[1] = Global.CYCLE_VIEW_ID;
        cyclepacket[2] = 0;
        cyclepacket[3] = 0;
        cyclepacket[4] = Global.STOPBYTE;
        Global.BTStreamQueue.add(cyclepacket);
        BTDataParser.mHandler.sendEmptyMessage(0);
    }

    /** Called when the user taps "LM" in the app. Enables Launch Mode */
    public void LaunchMode(View v) {
        byte[] launchpacket = new byte[5];
        launchpacket[0] = Global.STARTBYTE;
        launchpacket[1] = Global.LAUNCH_MODE_ID;
        launchpacket[2] = 0;
        launchpacket[3] = 0;
        launchpacket[4] = Global.STOPBYTE;
        Global.BTStreamQueue.add(launchpacket);
        BTDataParser.mHandler.sendEmptyMessage(0);
    }

    /** For testing purposes. Simulates a race start by setting throttle to 100% */
    @Deprecated
    public void RaceStart(View v) {
        Global.InputThrottle = 100d;
    }

    /** For testing purposes. Simulates crossing the finish line */
    @Deprecated
    public void CrossFinishLine(View v) {
        myDrivenLocation.SimulateCrossStartFinishLine();
    }

    /** This function is called when the user taps "DEMO/RACE" in the top right corner of the app. Changes between race and demo mode */
    public static void QuickChangeMode(View v) {
        DrivenSettings.QuickChangeMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                CycleView();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                CycleViewReverse();
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

	/* ======= */
	/* THREADS */
    /* ======= */

    /** Starts the stream reader and data logger threads */
    private void StartRaceMode() {
        StartStreamReader();
        StartDataLogger();
    }

    /** Starts the random generator */
    private void StartDemoMode() {
        StartRandomGenerator();
    }

    /** Starts the data logger thread (if not already running). Re-initializes the thread if needed */
    private void StartDataLogger() {
        try {
            if (mDataToCSVFile == null) {
                mDataToCSVFile = new DataToCsvFile();
                mDataToCSVFile.start();
            } else if (!mDataToCSVFile.isAlive()) {
                if (mDataToCSVFile.getState() != Thread.State.NEW) {
                    mDataToCSVFile = new DataToCsvFile();
                }
                mDataToCSVFile.start();
                MainActivity.myLogging.setText("LOGGING");
                MainActivity.myLogging.setTextColor(getResources().getColor(R.color.positive));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Starts the Bluetooth stream reader thread (if not already running). Re-initializes the thread if needed */
    private void StartStreamReader() {
        try {
            if (mBTStreamReader == null) {
                mBTStreamReader = new BTStreamReader();
                mBTStreamReader.start();
            } else if (!mBTStreamReader.isAlive()) {
                if (mBTStreamReader.getState() != Thread.State.NEW) {
                    mBTStreamReader = new BTStreamReader();
                }
                mBTStreamReader.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Starts the Bluetooth parser thread (if not already running). Re-initializes the thread if needed */
    private void StartDataParser() {
        try {
            if (mBTDataParser == null) {
                mBTDataParser = new BTDataParser(this);
                mBTDataParser.start();
            } else if (!mBTDataParser.isAlive()) {
                if (mBTDataParser.getState() != Thread.State.NEW) {
                    mBTDataParser = new BTDataParser(this);
                }
                mBTDataParser.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Starts the UDP sender thread (if not already running). Re-initializes the thread if needed */
    private void StartUDPSender() {
        try {
            if (mUDPSender == null) {
                mUDPSender = new UDPSender();
                mUDPSender.start();
            } else if (!mUDPSender.isAlive()) {
                if (mUDPSender.getState() != Thread.State.NEW) {
                    mUDPSender = new UDPSender();
                }
                mUDPSender.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Starts the random generator thread (if not already running). Re-initializes the thread if needed */
    private void StartRandomGenerator() {
        try {
            if (mRandomGenerator == null) {
                mRandomGenerator = new RandomGenerator();
                mRandomGenerator.start();
            } else if (!mRandomGenerator.isAlive()) {
                if (mRandomGenerator.getState() != Thread.State.NEW) {
                    mRandomGenerator = new RandomGenerator();
                }
                mRandomGenerator.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Stops the data logger thread (if running) */
    private void StopDataLogger() {
        if (mDataToCSVFile != null && mDataToCSVFile.getState() != Thread.State.TERMINATED) {
            mDataToCSVFile.cancel();
        }
        MainActivity.myLogging.setTextColor(getResources().getColor(R.color.negative));
        MainActivity.myLogging.setText("NO");
    }

    /** Stops the random generator thread (if running) */
    private void StopRandomGenerator() {
        if (mRandomGenerator != null && mRandomGenerator.getState() != Thread.State.TERMINATED) {
            mRandomGenerator.cancel();
        }
    }

    /** Stops the Bluetooth stream reader thread (if running) */
    private void StopStreamReader() {
        if (mBTStreamReader != null && mBTStreamReader.getState() != Thread.State.TERMINATED) {
            mBTStreamReader.cancel();
        }
    }

    /* ========== */
	/* OTHER SHIT */
    /* ========== */

    private boolean checkIfUIThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public void CycleView() {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            currentFragment = FragmentList.cycle();
            fragmentTransaction.replace(R.id.CenterView, currentFragment);
            fragmentTransaction.commit();
        } catch (Exception e) {
            e.printStackTrace();
            e.getMessage();
        }
    }

    public void CycleViewReverse() {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            currentFragment = FragmentList.reverseCycle();
            fragmentTransaction.replace(R.id.CenterView, currentFragment);
            fragmentTransaction.commit();
            currentFragment.UpdateFragmentUI();
        } catch (Exception e) {
            e.getMessage();
        }
    }

    private void ActivateLaunchMode() {
        // reset some key variables
        // lap counters
        Global.Lap = 0;

        // Amp hours
        Global.AmpHours = 0d;

        // Lap data if any exists
        Global.LapDataList.clear();

        LapTimer.stop();
        LapTimer.setBase(SystemClock.elapsedRealtime());
        myDrivenLocation.ActivateLaunchMode();
    }

    /** Returns the AppContext from a static context
     *
     * @return The application context
     */
    public static Context getAppContext() {
        return MainActivity.context;
    }

    /** Updates the TextView in the top-left corner of the app with the csv file name and size */
    private static void UpdateDataFileInfo() {
        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Global.DATA_FILE);
        MediaScannerConnection.scanFile(MainActivity.getAppContext(), new String[]{f.getAbsolutePath()}, null, null);
        Global.DataFileLength = f.length();
        if (Global.DataFileLength < 1024) {
            MainActivity.myDataFileSize.setText(String.valueOf(Global.DataFileLength) + " B");
        } else if (Global.DataFileLength < 1048576) {
            MainActivity.myDataFileSize.setText(String.format("%.2f", (float) Global.DataFileLength / 1024.0) + " KB");
        } else {
            MainActivity.myDataFileSize.setText(String.format("%.2f", (float) Global.DataFileLength / 1048576) + " MB");
        }
        myDataFileName.setText(Global.DATA_FILE);
    }

    /** Updates the TextView in the top-right corner of the app with the current Bluetooth connection status */
    private void UpdateBTStatus() {
        MainActivityHandler.post(new Runnable() {
            public void run() {
                switch (Global.BTState) {
                    case DISCONNECTED:
                        MainActivity.myBTState.setText("DISCONNECTED");
                        MainActivity.myBTState.setTextColor(getResources().getColor(R.color.negative));
                        break;
                    case CONNECTING:
                        MainActivity.myBTState.setText("CONNECTING");
                        MainActivity.myBTState.setTextColor(getResources().getColor(R.color.neutral));
                        break;
                    case CONNECTED:
                        MainActivity.myBTState.setText("CONNECTED");
                        MainActivity.myBTState.setTextColor(getResources().getColor(R.color.positive));
                        break;
                    case RECONNECTING:
                        MainActivity.myBTState.setText("RECONNECTING... [" + Global.BTReconnectAttempts + "]");
                        MainActivity.myBTState.setTextColor(getResources().getColor(R.color.neutral));
                        break;
                }
            }
        });
    }

    /** Updates the TextView at the bottom of the UI showing the lap number */
    public static void UpdateLap() {
        MainActivity.LapNumber.setText("L" + Global.Lap);
    }

    /** Updates the TextView at the top of the UI showing the BT device name */
    public static void UpdateBTCarName() {
        MainActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.myBTCarName.setText(Global.BTDeviceName + " :: " + Global.CarName);
            }
        });
    }

	/* =========================== */
	/* BTDATAPARSER IMPLEMENTATION */
    /* =========================== */

    /** This function is triggered by the Bluetooth data parser when it receives a {Cxx} packet.
     *  The driver will not be able to interact with the app as they will be wearing gloves and the phone will be in a waterproof case.
     *  A pushbutton in the cockpit is monitored by the Arduino for presses. If it detects the button, it will send a {Cxx} packet over Bluetooth
     */
    @Override
    public void onCycleViewPacket() {
        MainActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                CycleView();
            }
        });
    }

    /** This function is triggered by the Bluetooth data parser when it receives a {Lxx} packet.
     *  The driver will not be able to interact with the app as they will be wearing gloves and the phone will be in a waterproof case.
     *  A pushbutton in the cockpit is monitored by the Arduino for presses. If it detects the button, it will send a {Lxx} packet over Bluetooth
     */
    @Override
    public void onActivateLaunchModePacket() {
        MainActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                ActivateLaunchMode();
            }
        });
    }

    /* =============================== */
	/* BLUETOOTHMANAGER IMPLEMENTATION */
    /* =============================== */

    /** This function is triggered by BluetoothManager when a successful connection has been established with the Arduino. It receives a BluetoothSocket as the argument
     *
     * @param BTSocket  The BluetoothSocket which holds the connection to the Arduino
     */
    @Override
    public void onBluetoothConnected(final BluetoothSocket BTSocket) {
        Global.BTSocket = BTSocket;
        Global.BTState = Global.BTSTATE.CONNECTED;
        UpdateBTStatus();

        MainActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                showMessage(Global.BTDeviceName + " successfully connected");
                StartRaceMode();
            }
        });
    }

    @Override
    public void onBluetoothConnecting() {
        MainActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                showMessage("Connecting to " + Global.BTDeviceName + "...");
                Global.BTState = Global.BTSTATE.CONNECTING;
                UpdateBTStatus();
            }
        });
    }

    /** This function is triggered by BluetoothManager when an unsuccessful connection occurs */
    @Override
    public void onFailConnection() {
        MainActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                showMessage("Could not connect to " + Global.BTDeviceName + ". Please try again");
                Global.BTState = Global.BTSTATE.DISCONNECTED;
                UpdateBTStatus();
            }
        });
    }

    @Override
    public void onBluetoothDisconnected() {
        MainActivityHandler.post(new Runnable() {
            @Override
            public void run() {
                showMessage(Global.BTDeviceName + " disconnected");
                Global.BTState = Global.BTSTATE.DISCONNECTED;
                UpdateBTStatus();
                StopDataLogger();
                UpdateDataFileInfo();
            }
        });
    }

    /** This function is triggered by BluetoothManager when the Bluetooth is disabled on the users phone. This must be handled by MainActivity because here is the only place where another Intent can be launched     */
    @Override
    public void onBluetoothDisabled() {
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBluetooth, 0);
    }

    /** Called when BluetoothManager is in a reconnecting loop */
    @Override
    public void onBluetoothReconnecting() {
        Global.BTState = Global.BTSTATE.RECONNECTING;
        UpdateBTStatus();
    }

    /* =============================== */
	/* SETTINGSFRAGMENT IMPLEMENTATION */
    /* =============================== */

    @Override
    public void onSettingChanged(SharedPreferences sharedPreferences, String key) {}
}