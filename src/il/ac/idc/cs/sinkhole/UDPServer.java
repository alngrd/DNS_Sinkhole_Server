package il.ac.idc.cs.sinkhole;

import java.net.*;

/**
 * Communication component between client and DNSServer
 * responsible for receiving and sending messages
 */
public class UDPServer {
    private int port;
    private DatagramSocket serverSocket;
    private static final int PACKET_SIZE = 1024;

    private int destPort;
    private String destAddress;

    /**
     * constructor
     * @param portNum - int - port number to listen
     * @throws Exception
     */
    public UDPServer(int portNum) throws Exception {
        port = portNum;
        System.out.println(String.format("UDP Server is up and listening on port %s", port));
    }

    /**
     * Sends packet
     * @param datagramPacket - DatagramPacket - the packet to send
     * @throws Exception
     */
    public void sendPacket(DatagramPacket datagramPacket) throws Exception {
        serverSocket.send(datagramPacket);
        serverSocket.close();
    }

    /**
     * Waiting and receiving packet from client
     * @return DatagramPacket - the packet received
     * @throws Exception
     */
    public DatagramPacket receivePacket() throws Exception {
        serverSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[PACKET_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        serverSocket.receive(receivePacket);

        // sets port and address of the client for replying
        this.destPort = receivePacket.getPort();
        this.destAddress = receivePacket.getAddress().getHostAddress();
        return receivePacket;
    }

    /**
     * client address getter
     * @return String - address
     */
    public String getDestAddress(){
        return this.destAddress;
    }

    /**
     * Client port getter
     * @return - int - port
     */
    public int getDestPort() {
        return this.destPort;
    }

    /**
     * Method to close connection
     */
    public void closeConnection() {
        this.serverSocket.close();
    }
}
