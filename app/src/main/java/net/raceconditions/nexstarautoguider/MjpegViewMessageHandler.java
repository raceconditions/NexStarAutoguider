package net.raceconditions.nexstarautoguider;

public interface MjpegViewMessageHandler {
    public void onMessage(String message);
    public void onMessage(String message, int length);
}
