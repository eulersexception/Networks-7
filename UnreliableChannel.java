import java.net.DatagramPacket;

public class UnreliableChannel {

    public static DatagramPacket[] duplicatePacket(DatagramPacket packetIn) {
        return new DatagramPacket[] {packetIn, packetIn};
    }

    public static DatagramPacket losePacket(DatagramPacket packetIn) {
        return null;
    }

    public static DatagramPacket manipulatePacket(DatagramPacket packetIn) {
        byte[] payload = packetIn.getData();
        payload[12] ^= 1;
        packetIn.setData(payload);

        return packetIn;
    }
}
