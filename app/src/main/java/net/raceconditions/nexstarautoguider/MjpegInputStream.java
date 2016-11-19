package net.raceconditions.nexstarautoguider;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class MjpegInputStream extends DataInputStream {
    private static final String TAG = "MjpegInputStream";

    private final byte[] SOI_MARKER = {(byte) 0xFF, (byte) 0xD8};
    private final byte[] EOF_MARKER = {(byte) 0xFF, (byte) 0xD9};
    private final String CONTENT_LENGTH = "Content-Length";
    private final String X_TIMESTAMP = "X-Timestamp";
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 40000 + HEADER_MAX_LENGTH;
    private int mContentLength = -1;
    private double mEpochTimestamp = 0d;

    public MjpegInputStream(InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }

    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence) throws IOException {
        int seqIndex = 0;
        byte c;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
            c = (byte) in.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                seqIndex++;
                if (seqIndex == sequence.length) {
                    return i + 1;
                }
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int end = getEndOfSeqeunce(in, sequence);
        return (end < 0) ? (-1) : (end - sequence.length);
    }

    private Properties parseHeaderProperties(byte[] headerBytes) throws IOException, NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        return props;
    }

    public BitmapFrame readMjpegFrame() throws IOException {
        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();
        byte[] header = new byte[headerLen];
        readFully(header);
        try {
            Properties props = parseHeaderProperties(header);
            mEpochTimestamp = Double.parseDouble(props.getProperty(X_TIMESTAMP));
            mContentLength = Integer.parseInt(props.getProperty(CONTENT_LENGTH));
        } catch (NumberFormatException nfe) {
            nfe.getStackTrace();
            Log.d(TAG, "catch NumberFormatException hit", nfe);
            mContentLength = getEndOfSeqeunce(this, EOF_MARKER);
        }
        reset();
        byte[] frameData = new byte[mContentLength];
        skipBytes(headerLen);
        readFully(frameData);
        return new BitmapFrame(BitmapFactory.decodeStream(new ByteArrayInputStream(frameData)), mEpochTimestamp);
    }
}
