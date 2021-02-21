package il.ac.idc.cs.sinkhole;

import java.net.*;

/**
 * Communicating component for managing connections between DNSServer component and remote servers
 * managing the sending and receiving messages
 */
public class UDPClient
{
    private int port;
    private DatagramSocket clientSocket;
    private static final int PACKET_SIZE = 1024;

    /**
     * constructor
     * @param portNum
     * @throws Exception
     */
    public UDPClient(int portNum) throws Exception
    {
        port = portNum;
    }

    /**
     * sends packet and immediately waiting for response
     * @param digQuery - DatagramPacket - the query packet
     * @param inetAddress - InetAddress - the target address
     * @return - DatagramPacket - the query response
     * @throws Exception
     */
    public DatagramPacket sendPacketAndGetResponse(DatagramPacket digQuery, InetAddress inetAddress) throws Exception
    {
        clientSocket = new DatagramSocket();
        DatagramPacket query = new DatagramPacket(digQuery.getData(), digQuery.getLength(), inetAddress, port);
        clientSocket.send(query);

        return receivePacket();
    }

    /**
     * Receive packet method
     * @return - DatagramPacket - response packet
     * @throws Exception
     */
    private DatagramPacket receivePacket() throws Exception
    {
        byte[] receiveData = new byte[PACKET_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);
        clientSocket.close();

        return receivePacket;
    }

    /**
     * Method to close connection if needed
     * @throws Exception
     */
    public void closeConnection() throws Exception
    {
        clientSocket.close();
    }
}