import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.*;
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
    private static FSMSender fileSender = new FSMSender();

    private static void secureTransmissionViaUDP(String fileName, String ipTarget) throws IOException {

        InetAddress ip = InetAddress.getByName(ipTarget);
        File file = new File("src/"+fileName);
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
        int counter = 0;
        int timeouts = 0;

        long start = System.currentTimeMillis();
        while (bytesProcessed < sizeOfFile) {
            fileSender.processMsg(FSMSender.Msg.SEND);
            counter ++;
            System.out.println("round "+ counter);
            System.out.println("file bytes:" + bytesOfFile.length);

            int length;
            DatagramPacket packetOut;
            if(headerNotSent) {
                length = SIZE - HEADER_SIZE - 20;
                byte[] firstPacket = new byte[length + 20];
                System.arraycopy(fileNameAsBytes, 0, firstPacket, 0, fileNameLength);
                System.arraycopy(bytesOfFile, 0, firstPacket, 20, length);
                sendingData = FileSender.createChunkWithChecksum(alternatingBit, sendEndFlag, firstPacket);
                packetOut = new DatagramPacket(sendingData, sendingData.length, ip, DESTINATION_PORT);
            }
            else {
                length = Math.min(SIZE - HEADER_SIZE, sizeOfFile - bytesProcessed);
                System.out.println("data\nlength: " + length);
                System.out.println("bytes processed: " + bytesProcessed);
                sendingData = FileSender.createChunkWithChecksum(alternatingBit, sendEndFlag, Arrays.copyOfRange(bytesOfFile, bytesProcessed, bytesProcessed + length));
                packetOut = new DatagramPacket(sendingData, sendingData.length, ip, DESTINATION_PORT);
            }
            DatagramPacket packetIn = new DatagramPacket(receivingData, receivingData.length);
            socket.send(packetOut);

            boolean receiving = true;

            while (receiving) {
                try {
                    socket.receive(packetIn);
                    byte[] ack = packetIn.getData();

                    if (FileSender.checkACK(ack, alternatingBit)) { //check if checksum and is correct
                        bytesProcessed += length;
                        receiving = false;
                        alternatingBit ^= 1;
                        fileSender.processMsg(FSMSender.Msg.ALL_FINE);
                        if(headerNotSent) {
                            headerNotSent = false;
                        }
                    } else {
                        socket.send(packetOut);
                        counter++;
                        fileSender.processMsg(FSMSender.Msg.CORRUPT_OR_WRONG_BIT);
                    }
                } catch (SocketTimeoutException ex) {
                    fileSender.processMsg(FSMSender.Msg.TIMEOUT);
                    socket.send(packetOut);
                    counter++;
                    timeouts++;
                }
            }
            sendEndFlag = bytesProcessed + length < sizeOfFile ? FileSender.setFlag(1) : FileSender.setFlag(2);
        }
        long duration = System.currentTimeMillis() - start;
        printStats(bytesProcessed, calculateThroughput(duration, bytesProcessed), 0, 0, counter);
        socket.close();
        System.out.println("Timeouts: " +timeouts);
    }

    /**
     * Creates a chunk that contains source port, destination port, alternating bit, payload length, checksum and payload (in this order) as byte values.
     * Alternating bit is represented by one byte, payload length by lower three bytes. Port numbers are reduced to the lower two bytes, long value checksum to its lower four bytes.
     *
     * @param alternatingBit A byte representing zero or one.
     * @param payLoad        Raw message as array of bytes.
     * @return A byte array of maximum length 1400.
     */
    private static byte[] createChunkWithChecksum(byte alternatingBit, byte sendEndFlag, byte[] payLoad) {

        // creating byte array for header and payload - no checksum consideration yet
        byte[] newPayLoad = new byte[HEADER_SIZE + payLoad.length - 4];
        int index = 0;

        System.arraycopy(SOURCE_BYTES, 0, newPayLoad, index, SOURCE_BYTES.length);
        index += SOURCE_BYTES.length;

        System.arraycopy(DESTINATION_BYTES, 0, newPayLoad, index, DESTINATION_BYTES.length);
        index += DESTINATION_BYTES.length;

        newPayLoad[index] = alternatingBit;
        index++;

        newPayLoad[index] = sendEndFlag;
        index++;

        byte[] payloadLengthAsBytes = ByteBuffer.allocate(Integer.BYTES).putInt(payLoad.length).array();
        System.arraycopy(payloadLengthAsBytes, 2, newPayLoad, index, payloadLengthAsBytes.length - 2);
        index += payloadLengthAsBytes.length - 2;

        System.arraycopy(payLoad, 0, newPayLoad, index, payLoad.length);


        // generating checksum for payload + header
        long checksum = FileSender.getChecksum(newPayLoad);
        byte[] checksumBytes = ByteBuffer.allocate(Long.BYTES).putLong(checksum).array();


        // reuse of index variable
        index = 0;

        // result array - checksum will be added to existing information
        byte[] result = new byte[newPayLoad.length + 4];
        System.arraycopy(newPayLoad, 0, result, index, 8);
        index += 8;
        System.arraycopy(checksumBytes, 4, result, index, 4);
        index += 4;
        System.arraycopy(newPayLoad, 8, result, index, newPayLoad.length - 8);

        return result;
    }


    private static byte setFlag(int flag) {
        byte result = 0;

        if (flag == 1)
            result ^= 1;
        else if (flag == 2)
            result ^= 1 << 1;

        return result;
    }


    private static long getChecksum(byte[] payload) {
        CRC32 crc32 = new CRC32();
        crc32.update(payload);
        return crc32.getValue();
    }


    private static boolean checkACK(byte[] ack, int expectedBit) {

        int index = 0;
        byte[] intValues = new byte[5];
        byte[] longValue = new byte[8];
        CRC32 checksumTest = new CRC32();

        // bytes for source port (2), destination port(2) and sequence number of ack(1)
        System.arraycopy(ack, 0, intValues, index, intValues.length);
        checksumTest.update(intValues);
        index += intValues.length;

        // extracting given checksum
        System.arraycopy(ack, index, longValue, 4, 4);
        long checksum = ByteBuffer.wrap(longValue).getLong();

        return checksumTest.getValue() == checksum && intValues[4] == expectedBit;
    }

    public static void sendViaTCP(String fileName, String address, int port, long delayMillis, int flag) throws IOException, InterruptedException {

        byte[] message = Files.readAllBytes(new File("src/"+fileName).toPath());
        int length = message.length;
        InetAddress ip = InetAddress.getByName(address);
        Socket socket = new Socket(ip, port);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        byte[] packet;
        int packetNumber = 0;
        long bytesSent = 0;
        int currentIndex = 0;
        long start = System.currentTimeMillis();
        boolean headerNotSent = true;

        while (bytesSent < length) {

            if(headerNotSent) {
                byte[] nameAsArray = fileName.getBytes();
                packet = createChunk(++packetNumber, nameAsArray, 0);
                bytesSent += packet.length-4;
                output.write(packet);
                output.flush();
                headerNotSent = false;
            }
            else {
                packet = FileSender.createChunk(++packetNumber, message, currentIndex);
                output.write(packet);
                output.flush();
                bytesSent += packet.length -4;
                currentIndex = (currentIndex + SIZE - 4) % length;
            }
            // this can throw an InterruptedException
            if (flag != 0 && packetNumber % flag == 0)
                Thread.sleep(delayMillis);
        }
        long duration = System.currentTimeMillis() - start;
        socket.close();
        System.out.println("bytes actually sent: "+bytesSent);

        FileSender.printStats(bytesSent, FileSender.calculateThroughput(duration, bytesSent), delayMillis, flag, packetNumber);
    }


    private static void printStats(long bytesSent, float throughput, long delay, int flag, int packets) {
        String expectedRate;
        if(delay == 0) {
            expectedRate = "Theoretically unlimited";
        }
        else
            expectedRate = Float.toString((1400*flag)*8/(float)delay);

        System.out.println("Transmission Data for TCP:\n" +
                "\nTotal data\t\t\t" + (bytesSent * 8) + " bit" +
                "\nThroughput\t\t\t" + throughput + " kbit/s" +
                "\nDelay time in ms\t" + delay +
                "\nDelay time per\t\t" + flag + " packets" +
                "\nEstimated rate\t\t" + expectedRate +
                "\nTotal amount of packets sent:\t" + packets +
                "\n-------------------------------");
    }

    private static byte[] createChunk(int id, byte[] src, int srcIndex) {
        byte[] packID = FileSender.createByteArrayForChunk(id);
        byte[] chunk = new byte[SIZE];
        System.arraycopy(packID,0, chunk, 0, packID.length);
        System.arraycopy(src, srcIndex, chunk, packID.length, Math.min((src.length-srcIndex), (SIZE-packID.length)));
        return chunk;
    }


    private static byte[] createByteArrayForChunk(int id) {
        return ByteBuffer.allocate(Integer.SIZE/8).putInt(id).array();
    }


    private static float calculateThroughput(long duration, long sentBytes) {
        long div = 1000;
        long durationInSec = duration / div;
        long data_kbit = (sentBytes * 8) / div;
        return data_kbit / (float) durationInSec;
    }

    public static void main(String... args) throws IOException, InterruptedException {
        String ipAddress = args[0];
        String fileName = args[1];
        //sendViaTCP(fileName,ipAddress,80,0,0);
        secureTransmissionViaUDP(fileName, ipAddress);
    }
}
