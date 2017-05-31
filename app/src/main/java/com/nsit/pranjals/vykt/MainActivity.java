package com.nsit.pranjals.vykt;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import com.nsit.pranjals.vykt.adapters.ClientListAdapter;
import com.nsit.pranjals.vykt.models.Message;
import com.nsit.pranjals.vykt.network.Connection;

import java.util.ArrayList;

/**
 * Chat Selection Screen.
 */
public class MainActivity extends AppCompatActivity implements
        Connection.OnConnectionChangeListener, ClientListAdapter.OnClientItemSelectedListener {

    //==============================================================================================
    // PERMISSIONS AND RELATED STUFF!
    //==============================================================================================

    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    private static final int REQUEST_CODE_PERMISSION = 2;

    // Function with code to verify permissions for camera and storage in a go!
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int camera_permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.CAMERA);

        if (    write_permission != PackageManager.PERMISSION_GRANTED
                || read_permission != PackageManager.PERMISSION_GRANTED
                || camera_permission != PackageManager.PERMISSION_GRANTED ) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    //==============================================================================================
    //  Activity definition starts.
    //==============================================================================================

    private static final String CLIENT_NAME = "CLIENT_NAME";

    private EditText etBottomBar;
    private ArrayList<String> clients;
    private ClientListAdapter adapter;

    private SharedPreferences preferences;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                verifyPermissions(this);
            }
        }

        preferences = getPreferences(MODE_PRIVATE);

        if (preferences.contains(CLIENT_NAME)) {
            setServerAddressChangeFunctionality();
            App.userId = App.userId + ":" + preferences.getString(CLIENT_NAME, "");
        } else {
            findViewById(R.id.button_act_main_bottom_bar).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            setClientName();
                        }
                    }
            );
        }

        etBottomBar = (EditText) findViewById(R.id.et_act_main_bottom_bar);
        clients = new ArrayList<>();
        adapter = new ClientListAdapter(clients, this);
        ((ListView) findViewById(R.id.client_list)).setAdapter(adapter);

    }

    private void setServerAddressChangeFunctionality () {
        findViewById(R.id.button_act_main_bottom_bar).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setServerAddress();
                        Connection.connectToServerAsync(MainActivity.this);
                    }
                }
        );
    }

    private void setClientName () {
        String clientName = etBottomBar.getText().toString();
        if (!clientName.equals("")) {
            preferences.edit().putString(CLIENT_NAME, clientName).apply();
            App.userId = App.userId + ":" + clientName;
            setServerAddressChangeFunctionality();
        }
    }

    private void setServerAddress () {
        String serverAddress = etBottomBar.getText().toString();
        if (!serverAddress.equals(""))
            Connection.setIpAddress(serverAddress);
    }

    @Override
    public void wasServerConnected(boolean result) {
        Connection.fetchClientList(this);
    }

    @Override
    public void onClientListFetched (String[] clients) {
        this.clients.clear();
        for (String client : clients) {
            this.clients.add(client);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onMessageReceived(Message message) {

    }

    private void startChatActivity (String client) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(Connection.RECEIVER_TAG, client);
        startActivity(intent);
    }

    @Override
    public void onClientItemSelected(String client) {
        if (Connection.isConnected()) {
            startChatActivity(client);
        }
    }
}
