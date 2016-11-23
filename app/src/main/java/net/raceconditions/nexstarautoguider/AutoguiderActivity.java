package net.raceconditions.nexstarautoguider;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;

import org.opencv.android.OpenCVLoader;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;


public class AutoguiderActivity extends FragmentActivity {
    private MjpegView mv;
    private Context context;
    private String host = "http://0.0.0.0";
    private AsyncMjpegStreamTask streamTask;
    private boolean isRunning = false;
    private boolean isGuiding = false;

    public void onCreate(Bundle savedInstanceState) {
        context = this;
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        boolean inited = OpenCVLoader.initDebug();
        if(!inited)
            Log.d("Autoguider", "Not inited");

        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        host = sharedPrefs.getString("camera_url", host);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        mv = new MjpegView(this, new MjpegViewMessageHandler() {
            @Override
            public void onMessage(String message) {
                toastMessage(message);
            }
            @Override
            public void onMessage(String message, int length) {
                toastMessage(message, length);
            }
        });
        layout.addView(mv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.height = 200;
        lp.width = 200;
        ImageButton guideButton = new ImageButton(this);
        guideButton.setImageResource(R.mipmap.guide_icon);
        guideButton.setOnClickListener(guideButtonOnClickListener);
        guideButton.setLayoutParams(lp);
        guideButton.setBackgroundColor(Color.WHITE);
        layout.addView(guideButton);
        setContentView(layout);

        streamTask = getAsyncMjpegStreamTask();
    }

    private View.OnClickListener guideButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(isGuiding) {
                mv.stopGuiding();
            } else {
                mv.startGuiding();
            }
        }
    };

    private AsyncMjpegStreamTask getAsyncMjpegStreamTask() {
        return new AsyncMjpegStreamTask(new MjpegStreamHandler() {
            @Override
            public void onStreamInitialized(MjpegInputStream inputStream) {
                mv.setSource(inputStream);
                mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
                mv.setShowOverlay(true);
            }

            @Override
            public void onStreamFailed() {
                toastMessage("Stream failed to start");
            }
        });
    }

    private void toastMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toastMessage(final String message, final int length) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, length).show();
            }
        });
    }

    public void startPlayback() {
        if(!isRunning) {
            streamTask = getAsyncMjpegStreamTask();
            streamTask.execute(host);
            isRunning = true;
        } else {
            toastMessage("AutoGuider is already running");
        }
    }


    private static final int RESULT_SETTINGS = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_autoguider, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivityForResult(i, RESULT_SETTINGS);
                break;
            case R.id.action_stop:
                mv.stopPlayback();
                isRunning = false;
                toastMessage("AutoGuider has been stopped");
                break;
            case R.id.action_start:
                startPlayback();
                break;
        }
        return true;
    }
}
