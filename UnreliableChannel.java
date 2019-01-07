import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.sql.SQLOutput;

public class UnreliableChannel {
    public static int lost = 0;
    public static int manipulated =0;
    public static int duplicated = 0;

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

    public static DatagramPacket checkIfSomethingHappened(DatagramPacket packetIn, double pLose, double pDup, double pMan) {
        double pLoseCalc = Math.random();
        double pDupCalc = Math.random();
        double pManCalc = Math.random();
        DatagramPacket result = packetIn;
        if (pLoseCalc < pLose){
            byte[] data = new byte[1400];
            result.setData(data);
            lost ++;
        }
        else if (pDupCalc < pDup){
            duplicated++;
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
            } catch (SocketException e) {
                e.printStackTrace();
            }
            try {
                socket.send(result);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (pManCalc < pMan){
            manipulated++;
            byte[] payload = result.getData();
            payload[12] ^= 1;
            result.setData(payload);
        }
        return result;
    }
    public static void printStats(){
        System.out.println("Lost: "+ lost);
        System.out.println("Dupl: "+ duplicated);
        System.out.println("Manip: "+ manipulated);
    }
}
