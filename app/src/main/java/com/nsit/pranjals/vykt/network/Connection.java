package com.nsit.pranjals.vykt.network;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import com.nsit.pranjals.vykt.App;
import com.nsit.pranjals.vykt.enums.Expression;
import com.nsit.pranjals.vykt.models.Message;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Created by Pranjal on 31-05-2017.
 * The class to handle connections with the server.
 */

public class Connection {

    public static final String RECEIVER_TAG = "receiver";

    private static String ipAddress = "192.168.0.103";
    private static final int SERVER_PORT_NUMBER = 11111;
    private static final int SO_TIMEOUT = 0;

    private static final long RECEIVE_DURATION = 2000; // 2 seconds.

    private static Socket socket;
    private static ObjectInputStream in;
    private static ObjectOutputStream out;
    private static OnConnectionChangeListener listener;
    private static OnConnectionChangeListener messageReceiver = null;

    private static Handler uiHandler = new Handler(Looper.getMainLooper());

    //==============================================================================================
    //  ConnectTask
    //==============================================================================================

    private static class ConnectTask extends AsyncTask <Void, Void, Socket> {

        private OnConnectionChangeListener listener;

        ConnectTask (OnConnectionChangeListener listener) {
            this.listener = listener;
        }

        @Override
        protected Socket doInBackground(Void... voids) {
            Socket socket = null;
            try {
                socket = new Socket(ipAddress, SERVER_PORT_NUMBER);
                socket.setSoTimeout(SO_TIMEOUT);
                out = new ObjectOutputStream(
                        new BufferedOutputStream(
                                socket.getOutputStream()
                        )
                );
                out.flush();
                in = new ObjectInputStream(
                        new BufferedInputStream(
                                socket.getInputStream()
                        )
                );
                out.writeObject(new Message(0L, App.userId, "",
                        Message.MessageType.CONNECTION_REQUEST, "", Expression.NEUTRAL));
                out.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            return socket;
        }

        @Override
        protected void onPostExecute (Socket socket) {
            Connection.socket = socket;
            if (listener != null) {
                Connection.listener.wasServerConnected(socket != null);
            }
        }

    }

    public static void connectToServerAsync(OnConnectionChangeListener listener) {
        Connection.listener = listener;
        (new ConnectTask(listener)).execute();
    }

    private static boolean connectToServer (OnConnectionChangeListener listener) {
        if (socket == null || !socket.isConnected()) {
            try {
                socket = (new ConnectTask(listener)).execute().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (socket == null || !socket.isConnected())
                return false;
        }
        return true;
    }

    //==============================================================================================
    //  FetchTask
    //==============================================================================================

    private static class FetchClientListTask extends AsyncTask <Void, Void, String[]> {

        private OnConnectionChangeListener listener;

        FetchClientListTask (OnConnectionChangeListener listener) {
            this.listener = listener;
        }

        @Override
        protected String[] doInBackground(Void... voids) {
            if (!connectToServer(listener))
                return null;
            try {
                out.writeObject(new Message(Message.MessageType.GET_CLIENT_LIST_REQUEST));
                out.flush();
                Message message = (Message) in.readObject();
                return message.text.split(";");
            } catch (IOException|ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute (String[] clients) {
            listener.onClientListFetched(clients);
        }

    }

    public static void fetchClientList (OnConnectionChangeListener listener) {
        (new FetchClientListTask(listener)).execute();
    }

    //==============================================================================================
    //  SendTask
    //==============================================================================================

    private static class SendTask extends AsyncTask <Message, Void, Void> {

        @Override
        protected Void doInBackground(Message... messages) {
            if (!connectToServer(listener))
                return null;
            try {
                for (Message message : messages) {
                    out.writeObject(message);
                }
                out.writeObject(new Message(Message.MessageType.TERMINATOR));
                out.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            return null;
        }
    }

    public static void sendMessage (Message message) {
        (new SendTask()).execute(message);
    }

    //==============================================================================================
    //  ReceiveTask
    //==============================================================================================

    private static class ReceiveTask extends AsyncTask<Void, Message, Void> {

        private OnConnectionChangeListener listener;

        ReceiveTask (OnConnectionChangeListener listener) {
            this.listener = listener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (!connectToServer(listener))
                return null;
            try {
                out.writeObject(new Message(
                        0L,
                        App.userId,
                        App.userId,
                        Message.MessageType.RECEIVE_REQUEST,
                        "",
                        Expression.NEUTRAL)
                );
                out.flush();
                Message message;
                while (true) {
                    message = (Message) in.readObject();
                    if (message.type == Message.MessageType.TERMINATOR)
                        break;
                    publishProgress(message);
                }
            } catch (IOException|ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate (Message... messages) {
            for (Message message : messages) {
                if (listener != null)
                    listener.onMessageReceived(message);
            }
        }

        @Override
        protected void onPostExecute (Void object) {
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Connection.messageReceiver != null)
                        (new ReceiveTask(Connection.messageReceiver)).execute();
                }
            }, RECEIVE_DURATION);
        }

    }

    public static void receiveMessages (OnConnectionChangeListener listener) {
        messageReceiver = listener;
        (new ReceiveTask(listener)).execute();
    }

    public static void stopReceivingMessages () {
        messageReceiver = null;
    }

    //==============================================================================================
    //  Other Stuff
    //==============================================================================================

    public interface OnConnectionChangeListener {
        void wasServerConnected (boolean result);
        void onClientListFetched (String[] clients);
        void onMessageReceived (Message message);
    }

    public static void setIpAddress (String ipAddress) {
        Connection.ipAddress = ipAddress;
    }

}
