package net.raceconditions.nexstarautoguider;

public interface MjpegStreamHandler {
    public void onStreamInitialized(MjpegInputStream inputStream);
    public void onStreamFailed();
}
