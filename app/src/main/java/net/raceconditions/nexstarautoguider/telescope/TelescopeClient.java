package net.raceconditions.nexstarautoguider.telescope;

public interface TelescopeClient {
    void sendMessage(byte[] message);
    void stopClient();
    void startClient();
}
