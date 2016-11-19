package net.raceconditions.nexstarautoguider;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

public class OpticalFlowProcessor {

    private Mat lastFrame = null;
    private MatOfPoint lastCorners;
    double qualityLevel = 0.001;
    double minDistance = 10;
    int maxCorners = 25;

    double currentTimestamp;
    double lastTimestamp;
    double lastFeatureSelectionTimestamp;
    
    public OpticalFlowProcessor() {
        lastCorners = new MatOfPoint();
    }

    public Velocity findVelocity(BitmapFrame bitmapFrame) {
        Mat thisFrameGray = new Mat();
        MatOfPoint thisCorners = new MatOfPoint();
        currentTimestamp = bitmapFrame.getTimestamp();
        double velocityX = 0.0;
        double velocityY = 0.0;
        double milliseconds = 0;

        try {
            thisFrameGray = getGrayscaleFrameFromBitmap(bitmapFrame.getBitmap());
            thisCorners = findFeaturesToTrack(thisFrameGray);

            if(lastFrame != null) {
                Mat flow = new Mat(lastFrame.size(), CvType.CV_32FC2);

                MatOfPoint2f thisPoints = matOfPointTo2f(thisCorners);
                MatOfPoint2f lastPoints = matOfPointTo2f(lastCorners);

                MatOfByte status = new MatOfByte();
                MatOfFloat err = new MatOfFloat();
                Video.calcOpticalFlowPyrLK
                        (lastFrame, thisFrameGray, lastPoints, thisPoints,
                                status, err);

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

                //multiply by 10000 to get a readable number
                double varianceX = totalVarianceX / totalPoints * 10000;
                double varianceY = totalVarianceY / totalPoints * 10000;

                //Log.d("MjpegView status", status.dump());
                //Log.d("MjpegView err", err.dump());
                Log.d("VarianceX", Double.toString(varianceX));
                Log.d("VarianceY", Double.toString(varianceY));

                milliseconds = (currentTimestamp - lastTimestamp) * 1000;
                velocityX = varianceX/ milliseconds;
                velocityY = varianceY/ milliseconds;
                Log.d("VelocityX", Double.toString(velocityX));
                Log.d("VelocityY", Double.toString(velocityY));
                Log.d("Time", Double.toString(milliseconds));
            }
        } catch(Exception ex) {
            Log.e("MjpegView", "Something went wrong", ex);
        }

        lastTimestamp = currentTimestamp;
        lastFrame = thisFrameGray.clone();
        lastCorners.fromArray(thisCorners.toArray());

        return new Velocity(velocityX, velocityY, milliseconds);

        //Log.d("Last Features 2", "#" + lastCorners.dump());
    }

    private MatOfPoint2f matOfPointTo2f(MatOfPoint mop)
    {
        Point[] points = mop.toArray();
        return new MatOfPoint2f(points);
    }

    private MatOfPoint findFeaturesToTrack(Mat grayFrame) {
        MatOfPoint corners = new MatOfPoint();
        
        //TODO: Determine when we need to refresh features to track. Random recommendation is every 5 frames?
        if(lastFrame == null || lastFeatureSelectionTimestamp <= currentTimestamp - 1000) {
            lastFeatureSelectionTimestamp = currentTimestamp;
            Imgproc.goodFeaturesToTrack(grayFrame, corners, maxCorners, qualityLevel, minDistance);
        }
        return corners;
    }

    private Mat getGrayscaleFrameFromBitmap(Bitmap bm) {
        Mat thisFrameRgb = new Mat();
        Mat thisFrameGray = new Mat();
        Utils.bitmapToMat(bm, thisFrameRgb);
        Imgproc.cvtColor(thisFrameRgb, thisFrameGray, Imgproc.COLOR_RGB2GRAY);
        return thisFrameGray;
    }
}
