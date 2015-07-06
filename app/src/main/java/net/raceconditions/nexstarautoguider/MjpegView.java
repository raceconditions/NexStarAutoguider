package net.raceconditions.nexstarautoguider;

import java.io.IOException;

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

public class MjpegView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "MjpegView";

    public final static int POSITION_UPPER_LEFT  = 9;
    public final static int POSITION_UPPER_RIGHT = 3;
    public final static int POSITION_LOWER_LEFT  = 12;
    public final static int POSITION_LOWER_RIGHT = 6;

    public final static int SIZE_STANDARD   = 1;
    public final static int SIZE_BEST_FIT   = 4;
    public final static int SIZE_FULLSCREEN = 8;

    private MjpegViewThread thread;
    private MjpegInputStream mIn = null;
    private boolean showFps = false;
    private boolean mRun = false;
    private boolean surfaceDone = false;
    private Paint overlayPaint;
    private int overlayTextColor;
    private int overlayBackgroundColor;
    private int overlayPosition;
    private int dispWidth;
    private int dispHeight;
    private int displayMode;


    //thread
    private int frames_per_second = 0;

    private void init(Context context) {
        setKeepScreenOn(true);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        thread = new MjpegViewThread(holder);
        setFocusable(true);
        overlayPaint = new Paint();
        overlayPaint.setTextAlign(Paint.Align.LEFT);
        overlayPaint.setTextSize(48);
        overlayPaint.setTypeface(Typeface.DEFAULT);
        overlayTextColor = Color.WHITE;
        overlayBackgroundColor = Color.BLACK;
        overlayPosition = MjpegView.POSITION_LOWER_LEFT;
        displayMode = MjpegView.SIZE_STANDARD;
        dispWidth = getWidth();
        dispHeight = getHeight();
    }

    public void startPlayback() {
        if(mIn != null) {
            mRun = true;
            thread.start();
        }
    }

    public void stopPlayback() {
        mRun = false;
        boolean retry = true;
        while(retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                e.getStackTrace();
                Log.d(TAG, "catch IOException hit in stopPlayback", e);
            }
        }
    }

    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs); init(context);
    }

    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        thread.setSurfaceSize(w, h);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDone = false;
        stopPlayback();
    }

    public MjpegView(Context context) {
        super(context);
        init(context);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        surfaceDone = true;
    }

    public void showFps(boolean b) {
        showFps = b;
    }

    public void setSource(MjpegInputStream source) {
        mIn = source;
        startPlayback();
    }

    public void setOverlayPaint(Paint p) {
        overlayPaint = p;
    }

    public void setOverlayTextColor(int c) {
        overlayTextColor = c;
    }

    public void setOverlayBackgroundColor(int c) {
        overlayBackgroundColor = c;
    }

    public void setOverlayPosition(int p) {
        overlayPosition = p;
    }

    public void setDisplayMode(int s) {
        displayMode = s;
    }



    public class MjpegViewThread extends Thread {
        private SurfaceHolder mSurfaceHolder;
        private int frameCounter;
        private long start;
        private Bitmap bmpOverlay;

        PorterDuffXfermode mode;

        OpticalFlowProcessor opticalFlowProcessor;

        public MjpegViewThread(SurfaceHolder surfaceHolder) {
            mSurfaceHolder = surfaceHolder;
            mode = new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
            frameCounter = 0;
        }

        private Rect createDestinationRectangle(int bmw, int bmh) {
            int tempx;
            int tempy;
            if (displayMode == MjpegView.SIZE_STANDARD) {
                tempx = (dispWidth / 2) - (bmw / 2);
                tempy = (dispHeight / 2) - (bmh / 2);
                return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
            }
            if (displayMode == MjpegView.SIZE_BEST_FIT) {
                float bmasp = (float) bmw / (float) bmh;
                bmw = dispWidth;
                bmh = (int) (dispWidth / bmasp);
                if (bmh > dispHeight) {
                    bmh = dispHeight;
                    bmw = (int) (dispHeight * bmasp);
                }
                tempx = (dispWidth / 2) - (bmw / 2);
                tempy = (dispHeight / 2) - (bmh / 2);
                return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
            }
            if (displayMode == MjpegView.SIZE_FULLSCREEN){
                return new Rect(0, 0, dispWidth, dispHeight);
            }
            return null;
        }

        public void setSurfaceSize(int width, int height) {
            synchronized(mSurfaceHolder) {
                dispWidth = width;
                dispHeight = height;
            }
        }

        private Bitmap createTextOverlay(Paint p, String text) {
            Rect textRectangle = new Rect();
            p.getTextBounds(text, 0, text.length(), textRectangle);

            int bmpOverlayWidth  = textRectangle.width()+2;
            int bmpOverlayHeight = textRectangle.height()+2;

            Bitmap overlayBitmap = Bitmap.createBitmap(bmpOverlayWidth, bmpOverlayHeight, Bitmap.Config.ARGB_8888);
            Canvas overlayCanvas = new Canvas(overlayBitmap);

            p.setColor(overlayBackgroundColor);
            overlayCanvas.drawRect(0, 0, bmpOverlayWidth, bmpOverlayHeight, p);
            p.setColor(overlayTextColor);
            overlayCanvas.drawText(text, -textRectangle.left + 1, (bmpOverlayHeight / 2) - ((p.ascent() + p.descent()) / 2) + 1, p);

            return overlayBitmap;
        }

        public void run() {
            start = System.currentTimeMillis();
            Bitmap bm;
            int width;
            int height;
            Rect destRect;
            Canvas c = null;
            Paint p = new Paint();
            String fps = "";
            Velocity v;
            opticalFlowProcessor = new OpticalFlowProcessor();
            while (mRun) {
                if(surfaceDone) {
                    try {
                        c = mSurfaceHolder.lockCanvas();
                        synchronized (mSurfaceHolder) {
                            try {
                                bm = mIn.readMjpegFrame();

                                v = opticalFlowProcessor.findVelocity(bm);

                                destRect = createDestinationRectangle(bm.getWidth(), bm.getHeight());
                                c.drawColor(Color.BLACK);
                                c.drawBitmap(bm, null, destRect, p);
                                if(showFps) {
                                    p.setXfermode(mode);
                                    if(bmpOverlay != null) {
                                        height = ((overlayPosition & 1) == 1) ? destRect.top : destRect.bottom- bmpOverlay.getHeight();
                                        width  = ((overlayPosition & 8) == 8) ? destRect.left : destRect.right - bmpOverlay.getWidth();
                                        c.drawBitmap(bmpOverlay, width, height, null);
                                    }
                                    p.setXfermode(null);
                                    frameCounter++;
                                    if((System.currentTimeMillis() - start) >= 1000) {
                                        frames_per_second = frameCounter;
                                        fps = String.valueOf(frameCounter)+" fps";
                                        frameCounter = 0;
                                        start = System.currentTimeMillis();
                                    }

                                    bmpOverlay = createTextOverlay(overlayPaint, fps + "\nVelX: " + Double.toString(v.getVelX()) + "\nVelY: " + Double.toString(v.getVelY()));
                                }
                            } catch (IOException e) {
                                e.getStackTrace();
                                Log.d(TAG, "catch IOException hit in run", e);
                            }
                        }
                    } finally {
                        if (c != null) {
                            mSurfaceHolder.unlockCanvasAndPost(c);
                        }
                    }
                }
            }
        }


    }
}
