package hmi.hmiprojekt.Connection;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * @author Simon Zibat
 * helped by Minh Chi Nguyen where specified
 */
public class NearbyConnect {
    private ConnectionsClient connectionsClient;
    private String codeName;
    private File fileToSend;
    private Context context;
    private ConnectListener listener;

    /**
     * @author Patrick Strobel
     * interface to communicate with host activity which must implement all methods
     * more listeners for specific events can be added
     */
    public interface ConnectListener {
        void onTransferCompleted();
        void onZipperFailed();
    }

    public NearbyConnect(File fileToSend, ConnectionsClient connectionsClient, Context context){
        this.fileToSend = fileToSend;
        this.connectionsClient = connectionsClient;
        Random r = new Random();
        codeName = Integer.toString(r.nextInt(1000 - 1));
        this.context = context;

        // check if Activity implements ConnectListener
        if(context instanceof ConnectListener){
            listener = (ConnectListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " Must implement ConnectListener");
        }
    }

    /**
     * beginning of connection
     */
    private void startAdvertisingHere() {

        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        connectionsClient.startAdvertising(codeName, "hmi.hmiprojekt", connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're advertising!
                            Toast.makeText(context,"Melde als Austauschpartner",Toast.LENGTH_SHORT).show();

                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We were unable to start advertising.
                        });
    }

    /**
     * beginning of connection
     */
    private void startDiscoveryHere() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        connectionsClient.startDiscovery("hmi.hmiprojekt", endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            Toast.makeText(context,"Suche nach einem Austauschpartner",Toast.LENGTH_SHORT).show();
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
    }

    /**
     * if other side has been found come here, request connection automatically
     */
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    // An endpoint was found. We request a connection to it.
                    connectionsClient.stopDiscovery();
                    connectionsClient.stopAdvertising();
                    Toast.makeText(context,"Partner gefunden",Toast.LENGTH_SHORT).show();

                    connectionsClient.requestConnection(codeName, endpointId, connectionLifecycleCallback)
                            .addOnSuccessListener(
                                    (Void unused) -> {
                                    })
                            .addOnFailureListener(
                                    (Exception e) -> {
                                    });
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    // A previously discovered endpoint has gone away.
                }
            };

    /**
     * after connection has been requested come here, accept automatically
     * after establishing connection send/receive the file
     */
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    // Automatically accept the connection on both sides.
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            if(fileToSend!=null) {
                                try {
                                    Payload filePayload = Payload.fromFile(fileToSend);
                                    connectionsClient.sendPayload(endpointId, filePayload);
                                } catch (FileNotFoundException e) {
                                    Log.e("senden", e.getMessage());
                                }
                            }
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            // The connection was rejected by one or both sides.
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            // The connection broke before it was able to be accepted.
                            break;
                        default:
                            // Unknown status code
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                }
            };

    /**File has been received, unzip and move to app
     *
     * date and path of last file by Minh Chi Nguyen
     */
    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {

                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                        connectionsClient.disconnectFromEndpoint(endpointId);
                        Toast.makeText(context,"Datei empfangen, entzippe",Toast.LENGTH_SHORT).show();
                        File lastFilePath = getLatestFilefromDir(Environment.getExternalStorageDirectory() + "/Download/Nearby");
                        if (lastFilePath != null) {
                            try {
                                Date todayDate = Calendar.getInstance().getTime();
                                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                                String dirName = dateFormat.format(todayDate) + "_SharedTrip";
                                Thread thread = new Thread(){
                                    @Override
                                    public void run(){
                                        try {
                                            Zipper.unzip(lastFilePath.getAbsolutePath(), Environment.getExternalStorageDirectory() + "/roadbook/" + dirName);
                                            listener.onTransferCompleted();
                                        } catch (Exception e){
                                            Log.d("ThreadZipError",  e.getMessage());
                                            listener.onZipperFailed();
                                        }
                                    }
                                };
                                thread.start();
                            } catch (Exception e) {
                                Log.e("ZIP", e.getMessage());
                            }
                        }
                    }
                }
            };

    /**
     * @author Minh Chi Nguyen
     * @param dirPath directory to search through
     * @return the newest file in the directory
     */
    private File getLatestFilefromDir(String dirPath){
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }

        File lastModifiedFile = files[0];
        for (int i = 1; i < files.length; i++) {
            if (lastModifiedFile.lastModified() < files[i].lastModified()) {
                lastModifiedFile = files[i];
            }
        }
        return lastModifiedFile;
    }

    public void startAdvertising(){
        startAdvertisingHere();
    }

    public void startDiscovering(){
        startDiscoveryHere();
    }
}
