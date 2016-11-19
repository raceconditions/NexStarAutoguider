package net.raceconditions.nexstarautoguider;

import android.graphics.Bitmap;

/**
 * Created by Travis on 11/19/2016.
 */

public class BitmapFrame {
    private Bitmap bitmap;
    private Double timestamp;

    public BitmapFrame(Bitmap bitmap, Double timestamp) {
        this.bitmap = bitmap;
        this.timestamp = timestamp;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public Double getTimestamp() {
        return timestamp;
    }
}
