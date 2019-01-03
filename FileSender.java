import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.CRC32;


public class FileSender {

    private static final int SOURCE_PORT = 4242;
    private static final byte[] SOURCE_BYTES = Arrays.copyOfRange(ByteBuffer.allocate(Integer.BYTES).putInt(SOURCE_PORT).array(), 2, 4);
    private static final int DESTINATION_PORT = 2121;
    private static final byte[] DESTINATION_BYTES = Arrays.copyOfRange(ByteBuffer.allocate(Integer.BYTES).putInt(DESTINATION_PORT).array(), 2, 4);
    private static final int SIZE = 1400;
    private static final int HEADER_SIZE = SOURCE_BYTES.length + DESTINATION_BYTES.length + 1 + 1 + 2 + 4; // Header size in byte: 1 = alternatingBit, 1 = send-end-flag,  2 = payload length, 4 = lower 4 byte of checksum
    

    public static void secureTransmissionViaUDP(String fileName, String ipTarget) throws IOException {

        InetAddress ip = InetAddress.getByName(ipTarget);
        File file = new File(fileName);
        byte[] bytesOfFile = Files.readAllBytes(file.toPath());
        byte[] fileNameAsBytes = fileName.getBytes();
        int fileNameLength = fileNameAsBytes.length;

        int sizeOfFile = bytesOfFile.length;
        int bytesProcessed = 0;
        byte[] sendingData;
        byte[] receivingData = new byte[9];
        byte alternatingBit = Integer.valueOf(0).byteValue();
        byte sendEndFlag = FileSender.setFlag(1);


        DatagramSocket socket = new DatagramSocket(SOURCE_PORT);
        socket.setSoTimeout(250);

        boolean headerNotSent = true;

        while(headerNotSent) {
            int length = SIZE-HEADER_SIZE-fileNameLength;
            byte[] firstPacket = new byte[length+fileNameLength];
            System.arraycopy(fileNameAsBytes, 0, firstPacket, 0, fileNameLength);
            System.arraycopy(bytesOfFile, 0, firstPacket, fileNameLength, length);
            sendingData = FileSender.createChunkWithChecksum(alternatingBit, sendEndFlag, firstPacket);
            DatagramPacket packetOut = new DatagramPacket(sendingData, sendingData.length, ip, DESTINATION_PORT);
            DatagramPacket packetIn = new DatagramPacket(receivingData, receivingData.length);
            socket.send(packetOut);

            boolean receiving = true;

            while(receiving) {
                try {
                    socket.receive(packetIn);
                } catch (SocketTimeoutException ex) {
                    socket.send(packetOut);
                }

                byte[] ack = packetIn.getData();

                if(FileSender.checkACK(ack)) {
                    bytesProcessed += length;
                    receiving = false;
                    alternatingBit ^= 1;
                }
                else {
                    socket.send(packetOut);
                }
            }
        }

        while (bytesProcessed < sizeOfFile) {
            int length = Math.min(SIZE-HEADER_SIZE, sizeOfFile-bytesProcessed);
            sendingData = FileSender.createChunkWithChecksum(alternatingBit, sendEndFlag, Arrays.copyOfRange(bytesOfFile, bytesProcessed, length));
            DatagramPacket packetOut = new DatagramPacket(sendingData, sendingData.length, ip, DESTINATION_PORT);
            DatagramPacket packetIn = new DatagramPacket(receivingData, receivingData.length);
            socket.send(packetOut);

            boolean receiving = true;

            while(receiving) {
                try {
                    socket.receive(packetIn);
                } catch (SocketTimeoutException ex) {
                    socket.send(packetOut);
                }

                byte[] ack = packetIn.getData();

                if (FileSender.checkACK(ack)) { //check if checksum is correct
                    bytesProcessed += length;
                    receiving = false;
                    alternatingBit ^= 1;
                }
                else {
                    socket.send(packetOut);
                }
            }
            sendEndFlag = bytesProcessed + length < sizeOfFile? FileSender.setFlag(1) : FileSender.setFlag(2);
        }
        socket.close();
    }


    /**
     * Creates a chunk that contains source port, destination port, alternating bit, payload length, checksum and payload (in this order) as byte values.
     * Alternating bit is represented by one byte, payload length by lower three bytes. Port numbers are reduced to the lower two bytes, long value checksum to its lower four bytes.
     * @param alternatingBit A byte representing zero or one.
     * @param payLoad Raw message as array of bytes.
     * @return A byte array of maximum length 1400.
     */
    private static byte[] createChunkWithChecksum(byte alternatingBit, byte sendEndFlag, byte[] payLoad) {

        // creating byte array for header and payload - no checksum consideration yet
        byte[] newPayLoad = new byte[HEADER_SIZE+payLoad.length-4];
        int index = 0;

        System.arraycopy(SOURCE_BYTES,0, newPayLoad,index, SOURCE_BYTES.length);
        index += SOURCE_BYTES.length;

        System.arraycopy(DESTINATION_BYTES,0, newPayLoad, index, DESTINATION_BYTES.length);
        index += DESTINATION_BYTES.length;

        newPayLoad[index] = alternatingBit;
        index++;

        newPayLoad[index] = sendEndFlag;
        index++;

        byte[] payloadLengthAsBytes = ByteBuffer.allocate(Integer.BYTES).putInt(payLoad.length).array();
        System.arraycopy(payloadLengthAsBytes, 2, newPayLoad, index, payloadLengthAsBytes.length-2);
        index += payloadLengthAsBytes.length-1;

        System.arraycopy(payLoad, 0, newPayLoad, index, payLoad.length);


        // generating checksum for payload + header
        long checksum = FileSender.getChecksum(newPayLoad);
        byte[] checksumBytes = ByteBuffer.allocate(Long.BYTES).putLong(checksum).array();


        // reuse of index variable
        index = 0;

        // result array - checksum will be added to existing information
        byte[] result = new byte[newPayLoad.length+4];
        System.arraycopy(newPayLoad, 0, result, index, 8);
        index += 8;
        System.arraycopy(checksumBytes, 4, result, index, 4);
        index += 4;
        System.arraycopy(newPayLoad, 8, result, index, newPayLoad.length-8);

        return result;
    }


    private static byte setFlag(int flag) {
        byte result = 0;

        if(flag == 1)
            result ^= 1;
        else if(flag == 2)
            result ^= 1 << 1;

        return result;
    }


    private static long getChecksum(byte[] payload) {
        CRC32 crc32 = new CRC32();
        crc32.update(payload);
        return crc32.getValue();
    }


    private static boolean checkACK(byte[] ack) {

            int index = 0;
            byte[] intValues = new byte[5];
            byte[] longValue = new byte[8];
            CRC32 checksumTest = new CRC32();

            // bytes for source port (2), destination port(2) and sequence number of ack(4)
            System.arraycopy(ack, 0, intValues, index, intValues.length);
            checksumTest.update(intValues);
            index += intValues.length;

            // extracting given checksum
            System.arraycopy(ack, index, longValue, 4, 4);
            long checksum = ByteBuffer.wrap(longValue).getLong();

            return checksumTest.getValue() == checksum;
    }


    public static void main(String... args) {

        byte flag = 0;

        System.out.println("Zero flag: "+flag);

        flag ^= 1 << 0;

        System.out.println("Lowest bit set: "+flag);

        flag ^= 1 << 0;
        flag ^= 1 << 1;

        System.out.println("Lowest bit to zero, second bit to one: "+flag);

    }
}
