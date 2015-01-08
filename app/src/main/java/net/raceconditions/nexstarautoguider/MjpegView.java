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
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

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
    private int ovlPos;
    private int dispWidth;
    private int dispHeight;
    private int displayMode;

    public class MjpegViewThread extends Thread {
        private SurfaceHolder mSurfaceHolder;
        private int frameCounter = 0;
        private long start;
        private Bitmap ovl;

        //Optical Flow
        private Mat lastFrame = null;
        private MatOfPoint lastCorners;
        double qualityLevel = 0.001;
        double minDistance = 10;
        int maxCorners = 25;

        long currentTimeMillis;
        long lastTimeMillis;

        public MjpegViewThread(SurfaceHolder surfaceHolder, Context context) {
            mSurfaceHolder = surfaceHolder;
            lastCorners = new MatOfPoint();
        }

        private Rect destRect(int bmw, int bmh) {
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

        private Bitmap makeFpsOverlay(Paint p, String text) {
            Rect b = new Rect();
            p.getTextBounds(text, 0, text.length(), b);
            int bwidth  = b.width()+2;
            int bheight = b.height()+2;
            Bitmap bm = Bitmap.createBitmap(bwidth, bheight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            p.setColor(overlayBackgroundColor);
            c.drawRect(0, 0, bwidth, bheight, p);
            p.setColor(overlayTextColor);
            c.drawText(text, -b.left+1, (bheight/2)-((p.ascent()+p.descent())/2)+1, p);
            return bm;
        }

        public void run() {
            start = System.currentTimeMillis();
            PorterDuffXfermode mode = new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
            Bitmap bm;
            int width;
            int height;
            Rect destRect;
            Canvas c = null;
            Paint p = new Paint();
            String fps = "";
            Velocity v;
            lastCorners = new MatOfPoint();
            while (mRun) {
                if(surfaceDone) {
                    try {
                        c = mSurfaceHolder.lockCanvas();
                        synchronized (mSurfaceHolder) {
                            try {
                                bm = mIn.readMjpegFrame();

                                v = FindVelocity(bm);

                                destRect = destRect(bm.getWidth(),bm.getHeight());
                                c.drawColor(Color.BLACK);
                                c.drawBitmap(bm, null, destRect, p);
                                if(showFps) {
                                    p.setXfermode(mode);
                                    if(ovl != null) {
                                        height = ((ovlPos & 1) == 1) ? destRect.top : destRect.bottom-ovl.getHeight();
                                        width  = ((ovlPos & 8) == 8) ? destRect.left : destRect.right -ovl.getWidth();
                                        c.drawBitmap(ovl, width, height, null);
                                    }
                                    p.setXfermode(null);
                                    frameCounter++;
                                    if((System.currentTimeMillis() - start) >= 1000) {
                                        fps = String.valueOf(frameCounter)+" fps";
                                        frameCounter = 0;
                                        start = System.currentTimeMillis();
                                    }

                                    ovl = makeFpsOverlay(overlayPaint, fps + "\nVelX: " + Double.toString(v.getVelX()) + "\nVelY: " + Double.toString(v.getVelY()));
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

        private MatOfPoint2f MatOfPointTo2f(MatOfPoint mop)
        {
            //mop.convertTo(mop2f, CvType.CV_32FC2);
            Point[] points = mop.toArray();
            return new MatOfPoint2f(points);
        }

        private class Velocity{
            double velX;
            double velY;
            public Velocity(double velX, double velY){
                this.velX = velX;
                this.velY = velY;
            }

            public double getVelX(){
                return velX;
            }

            public double getVelY(){
                return velY;
            }
        }

        private Velocity FindVelocity(Bitmap bm) {
            Mat thisFrameGray = new Mat();
            MatOfPoint thisCorners = new MatOfPoint();
            currentTimeMillis = System.currentTimeMillis();
            double velocityX = 0.0;
            double velocityY = 0.0;

            try {
                thisCorners = new MatOfPoint();
                thisFrameGray = getGrayscaleFrameFromBitmap(bm);

                if(lastFrame == null)
                    //TODO: Determine when we need to refresh features to track. Random recommendation is every 5 frames?
                    Imgproc.goodFeaturesToTrack(thisFrameGray, thisCorners, maxCorners, qualityLevel, minDistance);
                if(lastFrame != null) {
                    Mat flow = new Mat(lastFrame.size(), CvType.CV_32FC2);

                    MatOfPoint2f thisPoints = MatOfPointTo2f(thisCorners);
                    MatOfPoint2f lastPoints = MatOfPointTo2f(lastCorners);

                    MatOfByte status = new MatOfByte();
                    MatOfFloat err = new MatOfFloat();
                    Video.calcOpticalFlowPyrLK
                            (lastFrame, thisFrameGray, lastPoints, thisPoints,
                                    status, err);

                    //TODO: Use lastPoints[i] + thisPoints[i] / time to calculate pixel distance traveled per unit of time.
                    Point[] lastPointsArray = lastPoints.toArray();
                    Point[] thisPointsArray = thisPoints.toArray();
                    byte[] statusArray = status.toArray();
                    int totalPoints = lastPointsArray.length;
                    int totalVarianceX = 0;
                    int totalVarianceY = 0;
                    for(int i = 0; i < lastPoints.toArray().length; i++) {
                        if(statusArray[i] == 1) {
                            totalVarianceX += lastPointsArray[i].x - thisPointsArray[i].x;
                            totalVarianceY += lastPointsArray[i].y - thisPointsArray[i].y;
                        }
                    }

                    double varianceX = totalVarianceX / totalPoints;
                    double varianceY = totalVarianceY / totalPoints;

                    //Log.d("MjpegView status", status.dump());
                    //Log.d("MjpegView err", err.dump());
                    Log.d("VarianceX", Double.toString(varianceX));
                    Log.d("VarianceY", Double.toString(varianceY));
                    Log.d("Time", Long.toString(currentTimeMillis - lastTimeMillis));

                    velocityX = varianceX/(currentTimeMillis - lastTimeMillis);
                    velocityY = varianceY/(currentTimeMillis - lastTimeMillis);
                    Log.d("VelocityX", Double.toString(velocityX));
                    Log.d("VelocityY", Double.toString(velocityY));


                    //Paint paint = new Paint();
                    //paint.setColor(Color.RED);
                    //c.drawText("VelX" + velX, 200, 100, paint);
                }
            } catch(Exception ex) {
                Log.e("MjpegView", "Something went wrong", ex);
            }

            lastTimeMillis = currentTimeMillis;
            lastFrame = thisFrameGray.clone();
            lastCorners.fromArray(thisCorners.toArray());

            return new Velocity(velocityX, velocityY);

            //Log.d("Last Features 2", "#" + lastCorners.dump());
        }

        private Mat getGrayscaleFrameFromBitmap(Bitmap bm) {
            Mat thisFrameRgb = new Mat();
            Mat thisFrameGray = new Mat();
            Utils.bitmapToMat(bm, thisFrameRgb);
            Imgproc.cvtColor(thisFrameRgb, thisFrameGray, Imgproc.COLOR_RGB2GRAY);
            return thisFrameGray;
        }
    }

    private void init(Context context) {
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        thread = new MjpegViewThread(holder, context);
        setFocusable(true);
        overlayPaint = new Paint();
        overlayPaint.setTextAlign(Paint.Align.LEFT);
        overlayPaint.setTextSize(12);
        overlayPaint.setTypeface(Typeface.DEFAULT);
        overlayTextColor = Color.WHITE;
        overlayBackgroundColor = Color.BLACK;
        ovlPos = MjpegView.POSITION_LOWER_RIGHT;
        displayMode = MjpegView.SIZE_STANDARD;
        dispWidth = getWidth();
        dispHeight = getHeight();

        //TextView view = (TextView)findViewById(R.id.txtFlow);
        //view.setText("TESTING AGAIN");
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
        ovlPos = p;
    }

    public void setDisplayMode(int s) {
        displayMode = s;
    }
}
