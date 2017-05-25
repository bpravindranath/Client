/**
 * Created by Barnabas_Ravindranath on 5/7/17.
 */

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class RavindranathClient {

    private TreeMap<Integer, byte[]> packetBuffer = new TreeMap<>(); //stores received UDP

    private final static String host = "localhost";
    private final static int port = 23251;
    private static final int udpPortnum = 23152;

    private static ExecutorService timingThread;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    private PrintWriter toServer;
    private BufferedReader fromServer;

    private Socket sock;
    private DatagramSocket udpSocket;
    private InetAddress fileSenderaddress;

    private static String servername =  null;

    private static int time_limit = 0;
    private static int packet_size =0;
    private int page_size = 0;
    private int fileSenderport;
    private int fileLength;
    private int packetNumber;
    private int total_packets;

    private byte[] receiveData = new byte[1000];

    private boolean packetsarrived = true;
    private boolean alldatahere = true;

    //Constructor
    public RavindranathClient() throws IOException{
        startNetwork();
    }

    //Main Thread Method
    public static void main (String[] args) throws InterruptedException, IOException {

        //Ask user to initialize variables
        try (Scanner input = new Scanner(System.in)) {
            System.out.println("Please enter the following data in the requested order with a single space after each input.\n" +
                    "1) Enter the Size of UDP Packets (bytes): \n" +
                    "2) Enter the time limit for all packets to arrive (milliseconds): \n" +
                    "3) Enter the Web address to get a HTTP request (e.g. www.towson.edu):");
            ArrayList list = new ArrayList();

            list.add(packet_size = input.nextInt());
            list.add(time_limit = input.nextInt());
            list.add(servername = input.next());

        }

        RavindranathClient client = null;

        try {client = new RavindranathClient();} catch (IOException e) {e.printStackTrace(); }

        client.go();

        client.ReceiveFile();

}

//Main method to begin initialize TCP and UDP connection and get server data
public void go() throws InterruptedException, SocketException {
        SendtoServer();
        IncomingDatafromServer();
    }

//Starts a TCP and UDP connection to Server on specified Port
private void startNetwork() throws IOException{
    try{
        System.out.println("You are connected to TCP Server on port " + port + " and UDP server on " + udpPortnum + "!\n");
        sock = new Socket(host, port);
        toServer = new PrintWriter(sock.getOutputStream(), true);
        fromServer =  new BufferedReader(new InputStreamReader(sock.getInputStream()));
        setUdpSocket();
    } catch (IOException e){
        System.err.println(e);
    }
}

//Sets up UDP Socket at specified Port
private void setUdpSocket() throws SocketException {
        udpSocket = new DatagramSocket(udpPortnum);
}

//Thread Method that calls IncomingReader() to get data from Server
public void IncomingDatafromServer() throws InterruptedException, SocketException {

    //loop check to make sure all data from server I am sending gets to Client.
    while(alldatahere) {
        threadPool.submit(new IncomingReader());
        threadPool.awaitTermination(5, TimeUnit.SECONDS);
    }
    threadPool.shutdown();
}

//Thread Handler: Receive Server's Message from TCP Port and Organizes to Appropriate Variables
public class IncomingReader implements Runnable {
    public void run() throws NullPointerException {

        try{
            while (fromServer.ready()) {
                int message = fromServer.read();
                switch (message){
                    case 'C': //Total_packets
                        total_packets = Integer.parseInt(fromServer.readLine().trim());
                        System.out.println("Server: The total packets being sent is " + total_packets);
                        alldatahere = false;
                        break;

                    case 'S':
                        System.out.println("Server: " + fromServer.readLine());
                        toServer.print('K');
                        toServer.println("Size OK!");
                        toServer.flush();
                        break;

                    case 'P': //page size (int)
                        page_size = Integer.parseInt((fromServer.readLine().trim()));
                        System.out.println("Server: The Page size is " + page_size + " bytes");
                        break;

                    case 'M':
                        System.out.println("Server: " + fromServer.readLine());
                        break;

                    default:
                        System.out.println("Error");
                }
            }

        } catch(Exception ex) {
            System.err.println(ex);
        }
    }

}

//sends User Input to Server: PacketSize, Web Server Name, and Time Limit
public void SendtoServer() throws InterruptedException {

        System.out.println("Sending data to Server...");
        toServer.print('P');
        toServer.flush();
        toServer.println(packet_size);
        toServer.flush();
        toServer.print('S');
        toServer.flush();
        toServer.println(servername);
        toServer.flush();
        toServer.print('T');
        toServer.println(time_limit);
        toServer.flush();

}

//Calls the method to Receive UDP packets
public void ReceiveFile() throws IOException, InterruptedException {

        System.out.println("Waiting for UDP files...");

        listenforUDPpacket();
}

//Method to get UDP packets from Server
public void listenforUDPpacket() throws IOException, InterruptedException {

    int x = 0;

    while(packetsarrived) {

        DatagramPacket incomingPacket = new DatagramPacket(receiveData, receiveData.length); //size of bytes serve declares?
        udpSocket.receive(incomingPacket);

        fileSenderaddress = incomingPacket.getAddress();
        fileSenderport = incomingPacket.getPort();
        byte[] temp = incomingPacket.getData();

        packetNumber = (int) temp[0];

        storePacketInfo(packetNumber, temp);
    }
    display_web_page_content();
}

//stores bytes into TreeMap, cleans bytes to get data, and checks if all UDP packets arrived
private void storePacketInfo(int packetNumber, byte[] data) throws IOException {
        if (!packetBuffer.containsKey(packetNumber)) {
            System.out.println("Packet " + packetNumber + " received!");
            data = cleanupPacket(data);
            fileLength = (int) data[1];
            packetBuffer.put(packetNumber, Arrays.copyOfRange(data, 2, data.length));
            checkAllPacketsArrived();
        }
    }

    //Extracts the data from udp packet and returns byte stream
    private byte[] cleanupPacket(byte[] lastPacketData) {
        ByteArrayOutputStream container = new ByteArrayOutputStream();
        for (byte data : lastPacketData) {
            if (data != 0) {
                container.write(data);
            }
        }
        return container.toByteArray();
    }

    //checks if all UDP packets arrived, sends a "Page Ok!" to Server over TCP, changes packetsarrived to false
    private void checkAllPacketsArrived() throws IOException {
        if (total_packets == packetNumber) {
            System.out.println("Sending 'Page Ok' to Server\n");

            String ack = "Page Ok!";

            DatagramPacket ackMessage = new DatagramPacket(ack.getBytes(), ack.getBytes().length, fileSenderaddress, fileSenderport);
            udpSocket.send(ackMessage);

            packetsarrived = false;

        }
    }

    //Displays HTML Page in correct order from TreeMap
    public void display_web_page_content() {
        for (Map.Entry<Integer, byte []> entry : packetBuffer.entrySet()) {
            String s = new String(entry.getValue());
            System.out.println(s);
        }
    }
}
