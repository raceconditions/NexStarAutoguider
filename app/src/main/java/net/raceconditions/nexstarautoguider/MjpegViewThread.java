package net.raceconditions.nexstarautoguider;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import net.raceconditions.nexstarautoguider.telescope.ConnectionEventHandler;
import net.raceconditions.nexstarautoguider.telescope.SlewSerializer;
import net.raceconditions.nexstarautoguider.telescope.TaskEventHandler;
import net.raceconditions.nexstarautoguider.telescope.TcpClient;
import net.raceconditions.nexstarautoguider.telescope.TcpConnectTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MjpegViewThread extends Thread {
    private static final String TAG = "MjpegViewThread";
    private final SurfaceHolder mSurfaceHolder;
    private PorterDuffXfermode mode;
    private OpticalFlowProcessor opticalFlowProcessor;
    private MjpegInputStream mjpegInputStream = null;
    private int frameCounter;
    private long start;
    private int currentFramesPerSecond;
    private Bitmap bmpOverlay;
    private Canvas displayCanvas;
    private Paint displayPaint;
    private MjpegViewMessageHandler messageHandler;
    private TcpClient mTcpClient;
    private Context context;
    private List<Velocity> recentVelocities = new ArrayList<Velocity>();

    private boolean surfaceReady = false;
    private boolean isStreaming = false;
    private boolean showOverlay = false;
    private boolean isConnected = false;
    private boolean readyToSlew = false;
    private boolean isGuiding = false;
    private Paint overlayPaint;
    private int overlayTextColor;
    private int overlayBackgroundColor;
    private int overlayPosition;
    private int dispWidth;
    private int dispHeight;
    private int displayMode;
    private double slewFactor;
    private int currentYFactored;
    private int currentXFactored;


    public MjpegViewThread(SurfaceHolder surfaceHolder, int width, int height, Context context, MjpegViewMessageHandler messageHandler) {
        mSurfaceHolder = surfaceHolder;
        mode = new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
        opticalFlowProcessor = new OpticalFlowProcessor();
        displayPaint = new Paint();
        this.messageHandler = messageHandler;
        this.context = context;

        overlayPaint = new Paint();
        overlayPaint.setTextAlign(Paint.Align.LEFT);
        overlayPaint.setTextSize(48);
        overlayPaint.setTypeface(Typeface.DEFAULT);
        overlayTextColor = Color.WHITE;
        overlayBackgroundColor = getColorWithAlpha(Color.BLACK, 0.2f);
        overlayPosition = MjpegView.POSITION_LOWER_LEFT;
        displayMode = MjpegView.SIZE_STANDARD;
        dispWidth = width;
        dispHeight = height;
        currentXFactored = 0;
        currentYFactored = 0;

        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        try {
            slewFactor = Double.valueOf(sharedPrefs.getString("slew_factor", String.valueOf(slewFactor)));
        }
        catch (Exception ex){
            messageHandler.onMessage("Unable to get settings value for slew factor");
        }
    }

    private static int getColorWithAlpha(int color, float ratio) {
        int newColor = 0;
        int alpha = Math.round(Color.alpha(color) * ratio);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        newColor = Color.argb(alpha, r, g, b);
        return newColor;
    }

    public void run() {
        Bitmap mjpegFrame;
        Velocity v;
        Rect videoDisplayRectangle;

        start = System.currentTimeMillis();
        frameCounter = 0;
        startConnection();
        while (isStreaming) {
            if (surfaceReady) {
                try {
                    displayCanvas = mSurfaceHolder.lockCanvas();
                    synchronized (mSurfaceHolder) {
                        try {
                            BitmapFrame bitmapFrame = mjpegInputStream.readMjpegFrame();
                            mjpegFrame = bitmapFrame.getBitmap();

                            videoDisplayRectangle = createVideoDisplayRectangle(mjpegFrame.getWidth(), mjpegFrame.getHeight());
                            displayCanvas.drawColor(Color.BLACK);
                            displayCanvas.drawBitmap(mjpegFrame, null, videoDisplayRectangle, displayPaint);
                            v = opticalFlowProcessor.findVelocity(bitmapFrame);
                            slewTelescope(v);
                            createDetailOverlay(videoDisplayRectangle, v);
                        } catch (IOException e) {
                            e.getStackTrace();
                            Log.d(TAG, "catch IOException hit in run", e);
                        }
                    }
                } finally {
                    if (displayCanvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(displayCanvas);
                    }
                }
            }
        }
        //stop any current movement of the telescope before shutdown
        sendSlewCommand(SlewSerializer.Axis.ALT, 0);
        sendSlewCommand(SlewSerializer.Axis.AZM, 0);
        stopConnection();
    }

    public void startGuiding() { this.isGuiding = true;}

    public void stopGuiding() { this.isGuiding = false;}

    public void setStreaming(boolean isStreaming) {
        this.isStreaming = isStreaming;
    }

    public MjpegInputStream getMjpegInputStream() {
        return mjpegInputStream;
    }

    public void setMjpegInputStream(MjpegInputStream mjpegInputStream) {
        this.mjpegInputStream = mjpegInputStream;
    }

    public void setDisplayMode(int s) {
        displayMode = s;
    }

    public void setShowOverlay(boolean b) {
        showOverlay = b;
    }

    public void setSurfaceSize(int width, int height) {
        synchronized (mSurfaceHolder) {
            dispWidth = width;
            dispHeight = height;
        }
    }

    public void setSurfaceReady(boolean surfaceReady) {
        this.surfaceReady = surfaceReady;
    }

    private void createDetailOverlay(Rect destRect, Velocity v) {
        int height;
        int width;
        if (showOverlay) {
            displayPaint.setXfermode(mode);
            if (bmpOverlay != null) {
                height = ((overlayPosition & 1) == 1) ? destRect.top : destRect.bottom - bmpOverlay.getHeight();
                width = ((overlayPosition & 8) == 8) ? destRect.left : destRect.right - bmpOverlay.getWidth();
                displayCanvas.drawBitmap(bmpOverlay, width, height, null);
            }
            displayPaint.setXfermode(null);
            frameCounter++;
            if ((System.currentTimeMillis() - start) >= 1000) {
                currentFramesPerSecond = frameCounter;
                frameCounter = 0;
                start = System.currentTimeMillis();
                readyToSlew = true;
            }

            bmpOverlay = createBitmapFromString(overlayPaint, currentFramesPerSecond + "fps\n" + Double.toString(v.getMilliseconds()) + "ms\nVelX: " + Double.toString(v.getVelX()) + "\nVelY: " + Double.toString(v.getVelY()));
        }
    }

    private Bitmap createBitmapFromString(Paint p, String text) {
        Rect textRectangle = new Rect();
        p.getTextBounds(text, 0, text.length(), textRectangle);

        int lineCount = text.split("\n").length + 1;
        int bmpOverlayWidth = textRectangle.width() + 2;
        int bmpOverlayHeight = (textRectangle.height() + 6) * lineCount;

        Bitmap overlayBitmap = Bitmap.createBitmap(bmpOverlayWidth, bmpOverlayHeight, Bitmap.Config.ARGB_8888);
        Canvas overlayCanvas = new Canvas(overlayBitmap);

        p.setColor(overlayBackgroundColor);
        overlayCanvas.drawRect(0, 0, bmpOverlayWidth, bmpOverlayHeight, p);
        p.setColor(overlayTextColor);
        drawMultilineText(text, 2, ((textRectangle.height() + 2) / 2) - ((p.ascent() + p.descent()) / 2) + 1, p, overlayCanvas);

        return overlayBitmap;
    }

    void drawMultilineText(String str, float x, float y, Paint paint, Canvas canvas) {
        String[] lines = str.split("\n");
        float txtSize = -paint.ascent() + paint.descent();

        if (paint.getStyle() == Paint.Style.FILL_AND_STROKE || paint.getStyle() == Paint.Style.STROKE) {
            txtSize += paint.getStrokeWidth(); //add stroke width to the text size
        }
        float lineSpace = txtSize * 0.2f;  //default line spacing

        for (int i = 0; i < lines.length; ++i) {
            canvas.drawText(lines[i], x, y + (txtSize + lineSpace) * i, paint);
        }
    }

    private Rect createVideoDisplayRectangle(int bmw, int bmh) {
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
        if (displayMode == MjpegView.SIZE_FULLSCREEN) {
            return new Rect(0, 0, dispWidth, dispHeight);
        }
        return null;
    }

    private void slewTelescope(Velocity v) {
        if(isGuiding) {
            currentXFactored += (int) Math.floor(v.getVelX() * slewFactor);
            currentYFactored += (int) Math.floor(v.getVelY() * slewFactor);

            if (readyToSlew) {
                readyToSlew = false;
                sendSlewCommand(SlewSerializer.Axis.AZM, currentXFactored);
                sendSlewCommand(SlewSerializer.Axis.ALT, currentYFactored);
            }
        }
    }

    /**
     * Connect to telescope
     */
    private void startConnection() {
        messageHandler.onMessage("Connecting to telescope...");
        try {
            new TcpConnectTask(new TaskListener(), new ConnectionListener(), context).execute("");
        } catch (final Exception ex) {
            Log.e("GPSLocationSync", "Telescope Connection Error", ex);
            messageHandler.onMessage("Telescope connection error");
        }
    }

    /**
     * Connect to telescope
     */
    private void stopConnection() {
        messageHandler.onMessage("Disconnecting from telescope...");
        try {
            if(mTcpClient != null) {
                mTcpClient.stopClient();
            }
        } catch (final Exception ex) {
            Log.e("GPSLocationSync", "Error disconnecting from telescope", ex);
            messageHandler.onMessage("Error disconnecting from telescope");
        }
    }

    /**
     * Slew the telescope on axis and speed in arcSeconds
     */
    private void sendSlewCommand(SlewSerializer.Axis axis, int arcSeconds) {
        if(!isConnected) {
            return;
        }
        try {
            byte[] slew = SlewSerializer.serialize(axis, arcSeconds >= 0 ? SlewSerializer.Direction.POS : SlewSerializer.Direction.NEG, Math.abs(arcSeconds));
            mTcpClient.sendMessage(slew);
        } catch (final Exception ex) {
            Log.e("GPSLocationSync", "Failed to send slew command", ex);
            messageHandler.onMessage("Failed to send slew command", Toast.LENGTH_SHORT);
        }
    }

    /**
     * Async cleanup/alert UI when closing TCP connection to telescope
     */
    private class TaskListener implements TaskEventHandler {
        public void onTaskCompleted(TcpClient c) {
            isConnected = false;
            if (mTcpClient != null) {
                mTcpClient = null;
                Log.e("GPSLocationSync", "Closed Connection");
                messageHandler.onMessage("The telescope connection is closed.");
            }
        }
    }

    /**
     * Async handle connection event/failure for TCP connection to telescope
     */
    private class ConnectionListener implements ConnectionEventHandler {
        @Override
        public void messageReceived(String message) {
//do nothing with messages
        }

        @Override
        public void connectionEstablished(TcpClient tcpClient) {
            mTcpClient = tcpClient;
            isConnected = true;
            messageHandler.onMessage("The telescope is successfully connected.");
            Log.i("GPSLocationSync", "Telescope connected");
        }

        @Override
        public void connectionFailed() {
            Log.e("GPSLocationSync", "Telescope Connection Failed");
            messageHandler.onMessage("Unable to connect to the telescope. Please check your connection and settings.");
        }
    }
}
