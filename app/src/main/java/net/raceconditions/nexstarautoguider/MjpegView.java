package net.raceconditions.nexstarautoguider;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class MjpegView extends SurfaceView implements SurfaceHolder.Callback {
    public final static int POSITION_UPPER_LEFT = 9;
    public final static int POSITION_UPPER_RIGHT = 3;
    public final static int POSITION_LOWER_LEFT = 12;
    public final static int POSITION_LOWER_RIGHT = 6;
    public final static int SIZE_STANDARD = 1;
    public final static int SIZE_BEST_FIT = 4;
    public final static int SIZE_FULLSCREEN = 8;
    private static final String TAG = "MjpegView";
    private MjpegViewThread thread;
    private MjpegViewMessageHandler messageHandler;


    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MjpegView(Context context, MjpegViewMessageHandler messageHandler) {
        super(context);
        this.messageHandler = messageHandler;
        init(context);
    }

    private void init(Context context) {
        setKeepScreenOn(true);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        thread = new MjpegViewThread(holder, getWidth(), getHeight(), context, messageHandler);
        setFocusable(true);
    }

    public void startPlayback() {
        if (thread.getMjpegInputStream() != null) {
            thread.setStreaming(true);
            thread.start();
        }
    }

    public void stopPlayback() {
        thread.setStreaming(false);
        boolean retry = true;
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                e.getStackTrace();
                Log.d(TAG, "catch IOException hit in stopPlayback", e);
            }
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        thread.setSurfaceSize(w, h);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        thread.setSurfaceReady(false);
        stopPlayback();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        thread.setSurfaceReady(true);
    }

    public void setShowOverlay(boolean b) {
        thread.setShowOverlay(b);
    }

    public void setSource(MjpegInputStream source) {
        thread.setMjpegInputStream(source);
        startPlayback();
    }

    public void setDisplayMode(int s) {
        thread.setDisplayMode(s);
    }


}
