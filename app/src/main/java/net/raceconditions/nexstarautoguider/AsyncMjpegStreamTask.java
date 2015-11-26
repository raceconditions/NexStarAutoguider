package net.raceconditions.nexstarautoguider;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URI;

public class AsyncMjpegStreamTask extends AsyncTask<String, Void, MjpegInputStream> {
    private static final String TAG = "AsyncMjpegStreamTask";
    MjpegStreamHandler handler;

    public AsyncMjpegStreamTask(MjpegStreamHandler handler) {
        this.handler = handler;
    }

    protected MjpegInputStream doInBackground(String... url) {
        HttpResponse res = null;
        DefaultHttpClient httpclient = new DefaultHttpClient();
        Log.d(TAG, "1. Sending http request");
        try {
            res = httpclient.execute(new HttpGet(URI.create(url[0])));
            Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
            if(res.getStatusLine().getStatusCode()!=200){
                handler.onStreamFailed();
                return null;
            }
            return new MjpegInputStream(res.getEntity().getContent());
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            Log.d(TAG, "Request failed-ClientProtocolException", e);
            handler.onStreamFailed();
            //Error connecting to camera
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Request failed-IOException", e);
            handler.onStreamFailed();
            //Error connecting to camera
        }

        return null;
    }

    protected void onPostExecute(MjpegInputStream inputStream) {
        handler.onStreamInitialized(inputStream);

    }
}
