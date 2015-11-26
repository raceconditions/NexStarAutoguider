package net.raceconditions.nexstarautoguider.telescope;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import java.sql.Connection;
/**
 * Created by ubuntu on 9/16/14.
 */
public class TcpConnectTask extends AsyncTask<String,String,TcpClient> {
    private TaskEventHandler taskListener;
    private ConnectionEventHandler connectionListener;
    private Context context;
    public TcpConnectTask(TaskEventHandler listener, ConnectionEventHandler connectionListener, Context context) {
        this.taskListener = listener;
        this.connectionListener = connectionListener;
        this.context = context;
    }
    @Override
    protected TcpClient doInBackground(String... message) {
        TcpClient mTcpClient = new TcpClient(connectionListener, context);
        try {
            mTcpClient.startClient();
        } catch (Exception ex) {
            Log.e("TcpConnectTask", "Error", ex);
            connectionListener.connectionFailed();
            return null;
        }
        return mTcpClient;
    }
    @Override
    protected void onPostExecute(TcpClient c) {
        taskListener.onTaskCompleted(c);
    }
}
