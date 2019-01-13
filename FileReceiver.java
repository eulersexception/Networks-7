/**
 * Networks 2018/2019
 * Lab 7:
 * - file transmission via UDP Alternating-Bit-Protocol compared to TCP
 * - unreliable channel
 * - finite state machine for FileSender and FileReceiver
 *
 * @author Erwin Kupris, kupris@hm.edu // Bahadir Süzer, suezer@hm.edu
 * @version 2019-01-13
 */

import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

public class FileReceiver {

    private static final int SOURCE_PORT = 2121;
    private static final byte[] SOURCE_BYTES = Arrays.copyOfRange(ByteBuffer.allocate(Integer.BYTES).putInt(SOURCE_PORT).array(), 2, 4);
    private static int DESTINATION_PORT;
    private static final byte[] DESTINATION_BYTES = Arrays.copyOfRange(ByteBuffer.allocate(Integer.BYTES).putInt(DESTINATION_PORT).array(), 2, 4);
    private static FSMReceiver fileReceiver = new FSMReceiver();

    public static void secureUDPReceiver() throws IOException {

        DatagramSocket socket = new DatagramSocket(SOURCE_PORT);
        socket.setSoTimeout(60000);
        byte[] data = new byte[1400];
        int packetsWrongAlt = 0;
        int packetsOkay = 0;
        int packetsCorrupt = 0;
        boolean receiving = true;
        byte[] wholeMessage = {};
        byte[] ackZero = FileReceiver.createACK(Integer.valueOf(0).byteValue());
        byte[] ackOne = FileReceiver.createACK(Integer.valueOf(1).byteValue());
        byte[] ack;
        boolean firstReceived = false;
        int expectedAltBit = 0;
        String fileName = "";
        long start = 0;
        int lostOnReceiver = 0;
        long end;
        int round = 0;
        boolean notLast = true;
        InetAddress ipSender = null;

        while (receiving) {
            DatagramPacket packetIn = new DatagramPacket(data, data.length);
            DatagramPacket actualPacket;
            try {

                while (notLast) {
                    round++;
                    socket.receive(packetIn);

                    if(!firstReceived) {
                        start = System.currentTimeMillis();
                    }
                    actualPacket = UnreliableChannel.checkIfSomethingHappened(packetIn, 0.1, 0.05, 0.05);

                    if (checkIfBytesAllZero(actualPacket.getData())) {
                        lostOnReceiver++;
                    } else if (isCorrupt(actualPacket.getData())) {
                        packetsCorrupt++;
                        fileReceiver.processMsg(FSMReceiver.Msg.IS_CORRUPT);
                    } else if (getAlternatingBit(getHeaderWithoutChecksum(actualPacket.getData())) != expectedAltBit) {
                        packetsWrongAlt++;
                        fileReceiver.processMsg(FSMReceiver.Msg.WRONG_ALTERNATING);
                    } else {
                        if (!firstReceived) {
                            fileName = new String(getFileNameFromFirst(getMessage(actualPacket.getData())));
                            ipSender = packetIn.getAddress();
                            DESTINATION_PORT = packetIn.getPort();
                            firstReceived = true;
                            System.out.println(fileName);
                            byte[] dataFromFirst = getMessage(actualPacket.getData());
                            wholeMessage = addToMessage(Arrays.copyOfRange(dataFromFirst, 20, dataFromFirst.length), wholeMessage);
                        } else {
                            wholeMessage = addToMessage(getMessage(actualPacket.getData()), wholeMessage);
                        }
                        notLast = FileReceiver.getFlag(packetIn.getData()) != 2;
                        packetsOkay++;
                        ack = getAlternatingBit(getHeaderWithoutChecksum(actualPacket.getData())) == 0 ? ackZero : ackOne;
                        expectedAltBit ^= 1;
                        DatagramPacket packetOut = new DatagramPacket(ack, ack.length, ipSender, DESTINATION_PORT);
                        socket.send(packetOut);
                        fileReceiver.processMsg(FSMReceiver.Msg.ALL_FINE);
                    }
                }

                end = System.currentTimeMillis();
                writeOutputFile(wholeMessage, fileName,"udp");

                System.out.println("Socket closed, total bytes written: " + wholeMessage.length);
                FileReceiver.printStats(packetsOkay, FileReceiver.calculateThroughput(start, end, wholeMessage.length), lostOnReceiver, "udp");
                System.out.println("Corrupt Packets: " + packetsCorrupt);
                System.out.println("Wrong Alt Bit: " + packetsWrongAlt);

                wholeMessage = new byte[0];
                fileName = "";
                notLast = true;
                firstReceived = false;
                start = 0;
                packetsCorrupt = 0;
                packetsOkay = 0;
                packetsWrongAlt = 0;
                expectedAltBit = 0;
                lostOnReceiver = 0;
                round = 0;

                } catch(SocketTimeoutException timeOut) {
                receiving = false;
            }
        }
        socket.close();
    }

    private static byte[] addToMessage(byte[] packet, byte[] message) {
        byte[] result = new byte[message.length + packet.length];
        System.arraycopy(message, 0, result, 0, message.length);
        System.arraycopy(packet, 0, result, message.length, packet.length);
        return result;
    }

    private static byte[] createACK(byte alternatingBit) {
        byte[] header = new byte[5];
        int index = 0;
        System.arraycopy(SOURCE_BYTES, 0, header, index, SOURCE_BYTES.length);
        index += SOURCE_BYTES.length;
        CRC32 crc32 = new CRC32();

        System.arraycopy(DESTINATION_BYTES, 0, header, index, DESTINATION_BYTES.length);
        index += DESTINATION_BYTES.length;
        header[index] = alternatingBit;

        crc32.update(header);

        byte[] checksum = ByteBuffer.allocate(Long.BYTES).putLong(crc32.getValue()).array();

        byte[] ack = new byte[9];

        index = 0;
        System.arraycopy(header, 0, ack, index, header.length);
        index += header.length;
        System.arraycopy(checksum, 4, ack, index, 4);

        return ack;
    }

    private static void writeOutputFile(byte[] message, String fileName, String protocol) {
        try (FileOutputStream fos = new FileOutputStream("src/rec_" + protocol + "_" + fileName.trim())) {
            fos.write(message);
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] getHeaderWithoutChecksum(byte[] datagram) {
        return Arrays.copyOfRange(datagram, 0, 8);
    }

    private static byte[] getFileNameFromFirst(byte[] datagram) {
        return Arrays.copyOfRange(datagram, 0, 20);
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

    private static boolean checkIfBytesAllZero(byte[] arr) {
        boolean result = true;

        for (byte element : arr) {
            if (element != 0) {
                result = false;
                break;
            }
        }

        return result;
    }

    private static boolean isCorrupt(byte[] datagram) {

        CRC32 checksum = new CRC32();

        byte[] header = FileReceiver.getHeaderWithoutChecksum(datagram);
        byte[] msg = FileReceiver.getMessage(datagram);

        byte[] totalBytes = new byte[header.length + msg.length];

        int index = 0;
        System.arraycopy(header, 0, totalBytes, index, header.length);

        index += header.length;
        System.arraycopy(msg, 0, totalBytes, index, msg.length);

        checksum.update(totalBytes);

        return FileReceiver.getChecksum(datagram) != checksum.getValue();
    }

    public static void startTCP(int port) throws IOException {
        long start = 0;
        ServerSocket server = new ServerSocket(port);
        int packetID;
        int packCounter = 0;
        long totalReceived = 0;
        byte[] data = new byte[1400];
        String fileName;
        byte[] wholeMessage = {};

        System.out.println("Connection testing....");
        Socket connection = server.accept();
        connection.setSoTimeout(5000);
        DataInputStream input = new DataInputStream(connection.getInputStream());
        System.out.println("Connection started");

        packetID = input.read(data, 0, 1400);
        fileName = new String(data).trim();

        while (packetID != -1) {
            if (start == 0) {
                start = System.currentTimeMillis();
            }
            else {
                packCounter++;
                packetID = input.read(data, 0, 1400);
                wholeMessage = addToMessage(Arrays.copyOfRange(data, 4, data.length), wholeMessage);
                totalReceived += data.length -4;
            }
        }
        writeOutputFile(wholeMessage, fileName, "tcp");
        System.out.println("TCP total bytes written: " + wholeMessage.length);
        System.out.println("TCP total bytes received: " + totalReceived);
        long end = System.currentTimeMillis();
        FileReceiver.printStats(packCounter, FileReceiver.calculateThroughput(start, end, totalReceived), 0, "tcp");
    }


    private static void printStats(int packCounter, float throughput, int packetsLost, String protocol) {
        System.out.println("Transmission Data for " + protocol + ":\n" +
                "\nAverage throughput:\t\t"+ throughput +" kbit/s"+
                "\nPackets received:\t\t"+packCounter+
                "\nPackets lost:\t\t\t"+packetsLost+
                "\n-------------------------------------");
    }

    private static float calculateThroughput(long start, long end, long receivedBytes) {
        long duration = end - start;
        return FileReceiver.calculateThroughput(duration, receivedBytes);
    }


    private static float calculateThroughput(long duration, long receivedBytes) {
        float div = 1000;
        float durationInSec = duration / div;
        float data_kbit = (receivedBytes * 8) / div;
        return data_kbit / durationInSec;
    }

    public static void main(String... args) throws IOException {
        startTCP(80);
        secureUDPReceiver();

    }

}
