package net.raceconditions.nexstarautoguider.telescope;

/**
 * Created by ubuntu on 9/7/14.
 */
        import android.app.AlertDialog;
        import android.content.Context;
        import android.content.DialogInterface;
        import android.content.SharedPreferences;
        import android.preference.PreferenceManager;
        import android.util.Log;
        import java.io.*;
        import java.net.InetAddress;
        import java.net.InetSocketAddress;
        import java.net.Socket;
        import java.net.SocketAddress;

public class TcpClient implements TelescopeClient {

    private String serverMessage;
    private ConnectionEventHandler mMessageListener = null;
    private boolean mRun = false;
    private Context mContext;
    private String host = "0.0.0.0";
    private int port = 0;
    private int timeout = 5000;

    OutputStream out;
    BufferedReader in;

    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TcpClient(ConnectionEventHandler listener, Context context) {
        mMessageListener = listener; mContext = context;

        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        host = sharedPrefs.getString("host", host);
        try {
            port = Integer.valueOf(sharedPrefs.getString("port_number", String.valueOf(port)));
        }
        catch (Exception ex){
            listener.connectionFailed();
        }
    }

    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */
    @Override
    public void sendMessage(byte[] message) {
        try {
            if (out != null) {
                out.write(message);
                out.flush();
            }
        }
        catch(IOException e)
        {
            Log.e("TCP", e.getMessage());
            mRun = false;
            mMessageListener.connectionFailed();
        }
    }

    /**
     * Stops TCP client connection by breaking connection loop
     */
    @Override
    public void stopClient() {
        mRun = false;
    }

    /**
     * Starts TCP client connection to telescope
     */
    @Override
    public void startClient() {

        mRun = true;

        try {
            InetAddress serverAddr = InetAddress.getByName(host);

            Log.e("TCP Client", "C: Connecting...");

            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(serverAddr, port), timeout);

            try {
                out = socket.getOutputStream();

                mMessageListener.connectionEstablished(this);
                Log.e("TCP Client", "C: Sent.");
                Log.e("TCP Client", "C: Done.");

                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                while (mRun) {
                    serverMessage = in.readLine();

                    if (serverMessage != null && mMessageListener != null) {
                        mMessageListener.messageReceived(serverMessage);
                    }
                    serverMessage = null;

                }

                Log.e("RESPONSE FROM SERVER", "S: Received Message: '" + serverMessage + "'");

            } catch (Exception e) {

                Log.e("TCP", "S: Error", e);
                mMessageListener.connectionFailed();

            } finally {
                socket.close();
            }

        } catch (Exception e) {
            Log.e("TCP", "C: Error", e);
            mMessageListener.connectionFailed();
        }
    }
}