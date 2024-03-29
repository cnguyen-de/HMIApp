package hmi.hmiprojekt;

import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;
import android.widget.RelativeLayout;


import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.ViewTarget;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.File;

import hmi.hmiprojekt.Connection.NearbyConnect;
import hmi.hmiprojekt.Connection.Zipper;
import hmi.hmiprojekt.Location.LocationHelper;
import hmi.hmiprojekt.MemoryAccess.Config;
import hmi.hmiprojekt.MemoryAccess.TripReader;
import hmi.hmiprojekt.MemoryAccess.TripWriter;
import hmi.hmiprojekt.TripComponents.Trip;
import hmi.hmiprojekt.Welcome.Application;
import hmi.hmiprojekt.Welcome.Preference;

public class MainActivity extends AppCompatActivity implements OnSuccessListener<Location>
        , NewTripDialog.NewTripDialogListener , NearbyConnect.ConnectListener, RenameTripDialog.RenameTripDialogListener {

    private static final int REQUEST_CHECK_SETTINGS = 100;
    private static final int REQUEST_VIEW_TRIP = 400;
    private static final int REQUEST_RECORD_TRIP = 700;
    private final static int PERMISSION_REQUEST_LOCATION = 200;
    private final static int PERMISSION_WRITE_EXTERNAL_STORAGE = 300;
    private final static int PERMISSION_BLUETOOTH_ADMIN = 500;
    private final static int PERMISSION_ACCESS_WIFI_STATE = 600;
    private LocationHelper locationHelper;
    private String tripName;
    private String tripToRename;
    private TripAdapter tripAdapter;

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    WifiManager wifiManager;
    NearbyConnect connectionsClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        //TODO uncommenting this will render app hardly usable on devices with navigation bar
        //w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getSupportActionBar().hide();
        locationHelper = new LocationHelper(this);
        setContentView(R.layout.activity_main);
        findViewById(R.id.mainFab).setOnClickListener(view -> showNewTripDialog());

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);
        } else {
            initRecycler();

            initShowcaseTutorial();

            tripAdapter.setOnItemClickListener((position, v) -> {
                Trip clickedTrip = tripAdapter.getTrip(position);
                Intent intent = new Intent(MainActivity.this, ViewTripActivity.class);
                intent.putExtra("tripDir", clickedTrip.getDir());
                startActivityForResult(intent, REQUEST_VIEW_TRIP);
            });
        }
    }

    private void initShowcaseTutorial() {
        //Get preference and checks if it's the first time MainActivity is loaded, if yes->showcase
        Preference preference = Application.getApp().getPreference();
        Log.e("Preference REQ", Boolean.toString(preference.isMAFirstTimeLaunch()));
        RelativeLayout.LayoutParams lps = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lps.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lps.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        int margin = ((Number) (getResources().getDisplayMetrics().density * 12)).intValue();
        lps.setMargins(margin, margin, margin, margin);

        /**
         * @author Chi Nguyen
         * This function checks for when MainActivity is first started so it can show the
         * walkthrough tutorials
         */
        if (preference.isMAFirstTimeLaunch()) {
            preference.setMAFirstTimeLaunch(false);
            Log.e("Preference SET", Boolean.toString(preference.isMAFirstTimeLaunch()));
            //Toast.makeText(this, "This is first time MA is loaded", Toast.LENGTH_SHORT).show();

            ShowcaseView showcase = new ShowcaseView.Builder(this)
                    .setTarget(new ViewTarget(R.id.mainFab, this))
                    .setContentTitle("EINSTIEGS TUTORIAL")
                    .setContentText("Hier klicken um einen neuen Trip aufzunehmen.")
                    .setStyle(R.style.CustomShowcaseTheme2)
                    .hideOnTouchOutside()
                    .build();
            showcase.setButtonPosition(lps);

        } else {
            //Code for when MainActivity is rendered again
            //Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
        }
    }

    private void initRecycler() {
        // create RecyclerView-Object and set LayoutManager
        RecyclerView mainRecycler = findViewById(R.id.recyclerview_main);
        mainRecycler.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        mainRecycler.setHasFixedSize(true); // always size matching constraint

        // read in the trips that are going to be showed on RecyclerView
        //TODO put in own method and use it in onResume instead of init method
        Trip[] trips = new Trip[0];
        try {
            trips = TripReader.readTrips();
        } catch (Exception e) {
            if (Config.createTripFolder()) {initRecycler(); return;}
            e.printStackTrace();
            Toast.makeText(getBaseContext()
                    , "Nich möglich auf die Daten zuzugreifen"
                    , Toast.LENGTH_SHORT).show();
        }

        // create Adapter and fill it with Trips
        tripAdapter = new TripAdapter(trips);
        mainRecycler.setAdapter(tripAdapter);
        tripAdapter.notifyDataSetChanged();
        registerForContextMenu(mainRecycler);
    }

    /**
     * @author Patrick Strobel
     * Starts Trip by asking a location request
     * result gets handled onSuccess()
     */
    private void startTrip() {
        locationHelper.startLocationRequest(this);
    }

    private void showNewTripDialog() {
        NewTripDialog tripDialog = new NewTripDialog();
        tripDialog.show(getSupportFragmentManager(), "new trip dialog");
    }

    private void showRenameTripDialog(){
        RenameTripDialog tripDialog = new RenameTripDialog();
        tripDialog.show(getSupportFragmentManager(), "rename trip dialog");
    }

    /**
     * @author Patrick Strobel
     * this methods checks if the locations settings are enabled to start a trip
     * the user gets asked by a familiar dialog to activate them first
     * result gets handled in onActivityResult()
     */
    protected void checkLocationSetting() {

        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(new LocationRequest())
                .build();

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(settingsRequest);

        task.addOnSuccessListener(this, locationSettingsResponse -> {
            // All location settings are satisfied.
            startTrip();
        });

        //OnFailure ask User to change settings
        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                //Location Service is off
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(MainActivity.this,
                            REQUEST_CHECK_SETTINGS);
                } catch (Exception sendEx) {
                    // Ignore error
                }
            }
        });
    }

    /**
     * @author Patrick Strobel
     * Activitys report back and user gets informed in case of irregularities
     * @param requestCode predefined integer to switch to cases
     * @param resultCode resultCode given by Activity
     * @param data additional extra data passed down from called Activity
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        //
                        startTrip();
                        break;
                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        Snackbar.make(findViewById(R.id.activity_main), "Trip kann ohne aktuellen Standort nicht aufgezeichnet werden", Snackbar.LENGTH_LONG).show();
                        break;
                    default:
                        break;
                }
                break;
            case REQUEST_VIEW_TRIP:
                if(resultCode == Activity.RESULT_CANCELED) {
                    Snackbar.make(findViewById(R.id.activity_main), "Der ausgewählte Trip scheint beschädigt zu sein", Snackbar.LENGTH_LONG).show();
                }
                break;
            case REQUEST_RECORD_TRIP:
                if(resultCode == Activity.RESULT_CANCELED) {
                    if (data != null) {
                        if (data.hasExtra("error")) {
                            Snackbar.make(findViewById(R.id.activity_main), data.getStringExtra("error"), Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(findViewById(R.id.activity_main), "Fehler beim Aufnehmen eines Trips", Snackbar.LENGTH_LONG).show();
                        }
                    }
                }
                break;
        }
    }

    /**
     * @author whoever needed specified permission
     * @param requestCode predefined integer to switch to cases
     * @param permissions specified permission
     * @param grantResults result of permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_LOCATION: {
                locationHelper.handlePermissionRequestResult(this, grantResults);
            }
            case PERMISSION_WRITE_EXTERNAL_STORAGE: {
                if(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    initRecycler();
                } else {
                    Toast.makeText(getBaseContext()
                            , "Unable to read your data"
                            , Toast.LENGTH_SHORT).show();
                }
            }
            case PERMISSION_BLUETOOTH_ADMIN:{
                if(!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                    Toast.makeText(getBaseContext(),
                            "Can't send or receive without Bluetooth",
                            Toast.LENGTH_SHORT).show();
                }
            }
            case PERMISSION_ACCESS_WIFI_STATE:{
                if(!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                    Toast.makeText(getBaseContext(),
                            "Can't send or receive without Wifi",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * @author Patrick Strobel
     * LocationHelper calls this method when it retrieves a location
     * starts RecordTrip Activity by passing the trip title and the current Position
     * @param location current Location of device
     */
    @Override
    public void onSuccess(Location location) {

        if (location != null) {
            Intent intent = new Intent(MainActivity.this, RecordTripActivity.class);
            intent.putExtra("currentPosition", new LatLng( location.getLatitude(), location.getLongitude()));
            intent.putExtra("tripName", tripName);
            startActivityForResult(intent, REQUEST_RECORD_TRIP);
        } else {
            Snackbar.make(findViewById(R.id.activity_main), "Positionsfehler", Snackbar.LENGTH_SHORT).show();
        }
    }

    // gets tripName from NewTripDialog
    @Override
    public void returnTripName (String tripName) {
        if (tripName != null && tripName.length() >= 1) {
            this.tripName = tripName;
            checkLocationSetting();
        } else {
            Snackbar.make(findViewById(R.id.activity_main), "Bitte geben sie einen Namen an.", Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * @author Simon Zibat
     *
     * Renames Trip to specified name
     * @param tripName new Name
     */
    @Override
    public void returnNewTripName (String tripName) {
        if (tripName != null && tripName.length() >= 1 && tripToRename != null) {
            File toRename = new File(tripToRename);
            String old = tripToRename.split("_", 2)[0];
            File newName = new File(old + "_" + tripName);
            toRename.renameTo(newName);
            initRecycler();
        } else {
            Snackbar.make(findViewById(R.id.activity_main), "Bitte geben sie einen Namen an.", Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * @author Simon Zibat
     * @param trip to send
     * managing sending side of file transfer
     */
    private void sendTrip(Trip trip){
        if(bluetoothAdapter==null){
            Toast.makeText(getApplicationContext(),"Bluetooth nicht verfügbar",Toast.LENGTH_SHORT).show();
        } else {
            Zipper.zip(trip.getDir().getAbsolutePath(),Environment.getExternalStorageDirectory() + "/roadbook/zip.zip");
            connectionsClient = new NearbyConnect(new File(Environment.getExternalStorageDirectory() + "/roadbook/zip.zip"),
                    Nearby.getConnectionsClient(this), this);
            WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (!bluetoothAdapter.isEnabled()) {
                setBluetoothAdapter();
                Toast.makeText(getApplicationContext(),"Bluetooth aktiviert",Toast.LENGTH_SHORT).show();
            }
            try {
                if (!wifiManager.isWifiEnabled()) {
                    setWifi();
                    Toast.makeText(getApplicationContext(),"Wifi aktiviert",Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e){
                Toast.makeText(getApplicationContext(),"Wifi nicht verfügbar",Toast.LENGTH_SHORT).show();
            }
            connectionsClient.startAdvertising();
        }
    }

    /**
     * @author Simon Zibat
     * managing receiving side of file transfer
     */
    private void receiveTrip(){
        if(bluetoothAdapter==null){
            Toast.makeText(getApplicationContext(),"Bluetooth nicht verfügbar",Toast.LENGTH_SHORT).show();
        } else {
            connectionsClient = new NearbyConnect(null, Nearby.getConnectionsClient(this), this);
            WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (!bluetoothAdapter.isEnabled()) {
                setBluetoothAdapter();
                Toast.makeText(getApplicationContext(),"Bluetooth aktiviert",Toast.LENGTH_SHORT).show();
            }
            try {
                if (!wifiManager.isWifiEnabled()) {
                    setWifi();
                    Toast.makeText(getApplicationContext(),"Wifi aktiviert",Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e){
                Toast.makeText(getApplicationContext(),"Wifi nicht verfügbar",Toast.LENGTH_SHORT).show();
            }
            connectionsClient.startDiscovering();
        }
    }

    /**
     * @author Simon Zibat
     * enable/disable bluetooth
     */
    public void setBluetoothAdapter(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_ADMIN}, PERMISSION_BLUETOOTH_ADMIN);
        }else {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            } else {
                bluetoothAdapter.disable();
            }
        }
    }

    /**
     * @ author Simon Zibat
     * enable/disable Wifi
     */
    public void setWifi(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_WIFI_STATE}, PERMISSION_ACCESS_WIFI_STATE);
        }else {
            wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            } else {
                wifiManager.setWifiEnabled(false);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = -1;
        try {
            position = tripAdapter.getPosition();
        } catch (Exception e) {
            Log.d("CONTEXTMENU ", e.getLocalizedMessage());
            return super.onContextItemSelected(item);
        }
        Trip trip = tripAdapter.getTrip(position);
        switch (item.getItemId()) {
            case R.id.send:
                sendTrip(trip);
                break;
            case R.id.delete:
                TripWriter.deleteTrip(trip);
                initRecycler();
                break;
            case R.id.rename:
                tripToRename = trip.getDir().getAbsolutePath();
                showRenameTripDialog();
                break;
        }
        return super.onContextItemSelected(item);
    }

    public void onReceiveTrip(View v) {
        receiveTrip();
    }

    @Override
    public void onTransferCompleted() {
        Snackbar.make(findViewById(R.id.activity_main), "Trip wurde empfangen!", Snackbar.LENGTH_LONG).show();
        runOnUiThread(this::initRecycler);
    }

    @Override
    public void onZipperFailed() {
        Snackbar.make(findViewById(R.id.activity_main), "Fehler im Unzip Thread", Snackbar.LENGTH_LONG).show();
        // TODO DELETE CORRUPTED SHARED TRIP
        // to show corrupted SharedTrip for debugging purpose
        runOnUiThread(this::initRecycler);
    }

}
