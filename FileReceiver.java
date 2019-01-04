import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;


public class FileReceiver {

    private static final int SOURCE_PORT = 2121;
    private static final byte[] SOURCE_BYTES = Arrays.copyOfRange(ByteBuffer.allocate(Integer.BYTES).putInt(SOURCE_PORT).array(), 2, 4);
    private static final int DESTINATION_PORT = 4242;
    private static final byte[] DESTINATION_BYTES = Arrays.copyOfRange(ByteBuffer.allocate(Integer.BYTES).putInt(DESTINATION_PORT).array(), 2, 4);
    private static final int SIZE = 9;
    private static final int HEADER_SIZE = SOURCE_BYTES.length + DESTINATION_BYTES.length + 1; // Header size for ACK in byte: 1 = Alternating Bit
    private static FSMReceiver fileReceiver;

    public static void secureUDPReceiver() throws IOException {

        DatagramSocket socket = new DatagramSocket(SOURCE_PORT);
        byte[] data = new byte[1400];
        int packetsWrong = 0;
        int packetsOkay = 0;
        socket.setSoTimeout(10000);
        boolean receiving = true;
        byte[] wholeMessage = {};
        int expectedAltBit = 0;

        while (receiving) {
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                socket.receive(packet);
                if (!notCorrupt(packet.getData())) {
                    packetsWrong++;
                    fileReceiver.processMsg(FSMReceiver.Msg.IS_CORRUPT);
                } else if (getAlternatingBit(getHeaderWithoutChecksum(packet.getData())) != expectedAltBit) {
                    packetsWrong++;
                    fileReceiver.processMsg(FSMReceiver.Msg.WRONG_ALTERNATING);
                }
                else {
                    packetsOkay++;
                    fileReceiver.processMsg(FSMReceiver.Msg.ALL_FINE);
                    wholeMessage = addToMessage(packet, wholeMessage);
                }
            } catch (SocketTimeoutException timeOut) {
                receiving = false;
            }
            expectedAltBit ^= 1;
        }
        socket.close();
        System.out.println("Socket closed, rec packets ok: " + packetsOkay + ", packets wrong: " + packetsWrong);
        //System.out.println("Message: " + Arrays.toString(wholeMessage));
        writeOutputFile(wholeMessage);
    }

    private static byte[] addToMessage(DatagramPacket packet, byte[] message) {
        byte[] newpart = ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), 4, packet.getLength()-8)).array();
        byte[] result = new byte[message.length + newpart.length];
        System.arraycopy(message, 0, result, 0, message.length);
        System.arraycopy(newpart, 0, result, message.length, newpart.length);
        return result;
    }

    private static void writeOutputFile(byte[] message) {
        try (FileOutputStream fos = new FileOutputStream("src/out.JPG")) {
            fos.write(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] getHeaderWithoutChecksum(byte[] datagram) {
        return Arrays.copyOfRange(datagram, 0, 8);
    }


    private static byte[] getMessage(byte[] datagram) {
        final byte[] lengthAsBytes = new byte[4];
        System.arraycopy(datagram, 6, lengthAsBytes, 2, 2);
        final int length = ByteBuffer.wrap(lengthAsBytes).getInt();
        return Arrays.copyOfRange(datagram, 12, length + 12);
    }


    private static long getChecksum(byte[] datagram) {
        final byte[] checksumAsBytes = new byte[8];
        System.arraycopy(datagram, 8, checksumAsBytes, 4, 4);
        return ByteBuffer.wrap(checksumAsBytes).getLong();
    }


    private static byte[] getSourcePort(byte[] header) {
        final byte[] sourceAsBytes = new byte[4];
        System.arraycopy(header, 0, sourceAsBytes, 2, 2);
        return sourceAsBytes;
    }


    private static byte[] getDestinationPort(byte[] header) {
        final byte[] destAsBytes = new byte[4];
        System.arraycopy(header, 2, destAsBytes, 2, 2);
        return destAsBytes;
    }


    private static int getAlternatingBit(byte[] header) {
        return (int) header[4];
    }


    private static byte getFlag(byte[] header) {
        return header[5];
    }


    private static int getMsgLength(byte[] header) {
        final byte[] lengthAsBytes = new byte[4];
        System.arraycopy(header, 6, lengthAsBytes, 2, 2);
        return ByteBuffer.wrap(lengthAsBytes).getInt();
    }


    private static boolean notCorrupt(byte[] datagram) {

        CRC32 checksum = new CRC32();

        byte[] header = FileReceiver.getHeaderWithoutChecksum(datagram);
        byte[] msg = FileReceiver.getMessage(datagram);

        byte[] totalBytes = new byte[header.length + msg.length];

        int index = 0;
        System.arraycopy(header, 0, totalBytes, index, header.length);

        index += header.length;
        System.arraycopy(msg, 0, totalBytes, index, msg.length);

        checksum.update(totalBytes);

        return FileReceiver.getChecksum(datagram) == checksum.getValue();
    }

    public static void main(String... args) throws IOException{
        fileReceiver = new FSMReceiver();
        secureUDPReceiver();
    }


}
