package com.nsit.pranjals.vykt;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.nsit.pranjals.vykt.models.Message;
import com.nsit.pranjals.vykt.network.Connection;

/**
 * Chat Selection Screen.
 */
public class MainActivity extends AppCompatActivity implements
        Connection.OnConnectionChangeListener {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

        Connection.connectToServerAsync(this);

    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    public void wasServerConnected(boolean result) {
        Connection.fetchClientList(this);
    }

    @Override
    public void onClientListFetched (String[] clients) {
        Log.v("vykt27", "clients length : " + clients.length);
        startChatActivity();
    }

    @Override
    public void onMessageReceived(Message message) {

    }

    private void startChatActivity () {
        Intent intent = new Intent(this, ChatActivity.class);
        //intent.putExtra(Connection.RECEIVER_TAG, "dummy");
        startActivity(intent);
    }

}
