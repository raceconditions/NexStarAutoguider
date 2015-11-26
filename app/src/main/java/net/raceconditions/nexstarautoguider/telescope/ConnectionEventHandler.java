package net.raceconditions.nexstarautoguider.telescope;

public interface ConnectionEventHandler {
    public void messageReceived(String message);
    public void connectionEstablished(TcpClient c);
    public void connectionFailed();
}
