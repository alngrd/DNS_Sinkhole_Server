package il.ac.idc.cs.sinkhole;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashSet;

/**
 * Backend Component - managing the logic of request and responses, messages manipulations
 * and client - server communication
 */
public class DNSServer {
    private static final String ROOT_SERVER_PATH = ".root-servers.net";
    private static final int PORT = 5300;
    private static final int HEADER_SIZE = 12;
    private static final int DNS_PORT = 53;
    private static final int MAX_REQUESTS = 16;
    private static final int DIG_TIMEOUT = 15000;
    private UDPClient udpClient;
    private UDPServer udpServer;
    private HashSet<String> blackList;
    private boolean blackListEnabled;

    /**
     * constructor for the DNSServer - init udp server and client and creates blacklist data structure if enabled
     * @param path - String - path to blacklist file or empty if no path received
     */
    public DNSServer(String path) {
        try {
            udpClient = new UDPClient(DNS_PORT);
            udpServer = new UDPServer(PORT);

            if (!path.equals("")) {
                createBlackListSet(path);
                this.blackListEnabled = true;
            }

        } catch (Exception e) {
            System.err.println("Failed set up client/server");
        }
    }

    /**
     * Main flow, running server, waiting for requests, and managing the iterated dns query
     */
    public void listenAndReact() {
        while (true) {
            DatagramPacket dnsResponse = null;
            InetAddress queryAddress = null;
            DatagramPacket query = null;

            // receiving dig query
            try {
                query = udpServer.receivePacket();
            } catch (Exception e) {
                System.err.println("Handled Error while trying to receive packet - check ports");
                continue;
            }

            // check if blacklist enabled and if query is in blacklist
            if (blackListEnabled && isBlackListed(query)) {
                try {
                    // sends 'status:NXDOMAIN' response
                    udpServer.sendPacket(buildUserPacket(query, true));
                } catch (Exception e) {
                    System.err.println("Build response Failed");
                }
                continue;
            }

            try {
                queryAddress = getRandomRootServer();
            } catch (Exception e) {
                System.err.println("Couldn't get root server address, try again");
                udpServer.closeConnection();
                continue;
            }

            try {
                dnsResponse = udpClient.sendPacketAndGetResponse(query, queryAddress);
            } catch (Exception e) {
                System.err.println("Failed to send/receive packet while sending to querying servers");
                udpServer.closeConnection();
                continue;
            }


            int numOfRequests = 1;
            // doing the iterating part of the request until final address is reached
            // or num of request exceeded threshold
            while (addressNotReached(dnsResponse) && numOfRequests <= MAX_REQUESTS) {
                try {
                    queryAddress = getFirstAuthority(dnsResponse);
                } catch (Exception e) {
                    System.err.println("Couldn't get address from response's RData");
                    udpServer.closeConnection();
                }

                try {
                    dnsResponse = udpClient.sendPacketAndGetResponse(query, queryAddress);
                } catch (Exception e) {
                    System.err.println("Failed to send/receive packet while sending to querying servers");
                    udpServer.closeConnection();
                    break;
                }

                numOfRequests++;
            }

            // dealing with non existing domain names
            if (isNumOfAnswerRecordsZero(dnsResponse)) {
                try {
                    // sends response with status:NXDOMAIN
                    udpServer.sendPacket(buildUserPacket(query, true));
                } catch (Exception e) {
                    System.err.println("Build response Failed");
                }
                continue;
            }

            // dealing with error codes
            if (!isResponseCodeNOERROR(dnsResponse)) {
                System.err.println("Error in Response code waiting dig request to timeout");
                try {
                    Thread.sleep(DIG_TIMEOUT);
                } catch (Exception ex) {
                    System.err.println(ex.getMessage());
                }
                udpServer.closeConnection();
            } else if (!isNumOfAnswerRecordsZero(dnsResponse)) {
                try {
                    // sends final InetAddress to client
                    udpServer.sendPacket(buildUserPacket(dnsResponse, false));
                } catch (Exception e) {
                    System.err.println("Failed to send packet while sending to user");
                    udpServer.closeConnection();
                }
            }
        }
    }

    /**
     * Inits the HashSet for blacklist domains
     * @param blackListPath - String - path to file
     */
    private void createBlackListSet(String blackListPath) {
        this.blackList = new HashSet<String>();

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(
                    blackListPath));
            String line = reader.readLine();
            while (line != null) {
                blackList.add(line);
                line = reader.readLine();
            }
            reader.close();
        } catch (Exception e) {
            System.err.println("Error Handling BlackList file");
        }
    }


    /**
     * Gets a request, retrieves address as string and checks if contained in blacklist
     * @param request - DatagramPacket - dig query
     * @return boolean - whether contained in blacklist or not
     */
    private boolean isBlackListed(DatagramPacket request) {
        String domainName = retrieveName(request);
        return blackList.contains(domainName);
    }

    /**
     * Retrieves the domain name from given request
     * @param request - DatagramPacket - dig query
     * @return - String - the domain name as string
     */
    private String retrieveName(DatagramPacket request) {
        return getAddressFromData(request, HEADER_SIZE);
    }

    /**
     * Builds the packet to send back to client
     * @param datagramPacket - DatagramPacket - response packet from dns server
     * @param isNXDOMAIN - boolean - if true, configure the response as NXDOMAIN
     * @return - DatagramPacket - the final packet to send to the client
     * @throws Exception
     */
    private DatagramPacket buildUserPacket(DatagramPacket datagramPacket, boolean isNXDOMAIN) throws Exception {
        byte[] data = datagramPacket.getData();
        // sets the QR RD RA flags
        data[2] = (byte) ((data[2] & -5) | 0b10000000);
        data[3] = (byte) (data[3] | 0b10000000);

        if (isNXDOMAIN) {
            // changing error code to ERROR CODE 3
            data[3] = (byte) ((data[3] & 0b11110000) | 3);
        }

        int destPort = udpServer.getDestPort();
        InetAddress destAddress = InetAddress.getByName(udpServer.getDestAddress());

        DatagramPacket packet = new DatagramPacket(data, datagramPacket.getLength(), destAddress, destPort);

        return packet;
    }

    /**
     * Gets the first Authority from given response
     * @param datagramPacket - DatagramPacket - given response
     * @return - InetAddress - the address of the first authority
     * @throws Exception
     */
    private InetAddress getFirstAuthority(DatagramPacket datagramPacket) throws Exception {

        String address = getAddressFromData(datagramPacket, getFirstRdata(datagramPacket));
        return InetAddress.getByName(address);
    }

    /**
     * Returns an int pointing to the beginning of the RData
     * @param datagramPacket - DatagramPacket - the response packet
     * @return - int - offset of the beginning of the RData
     */
    private int getFirstRdata(DatagramPacket datagramPacket) {
        byte[] data = datagramPacket.getData();
        int iter = HEADER_SIZE;

        // Skip Query fields
        while (data[iter] != 0) {
            iter++;
        }
        iter += 5;

        // Skip RR format to Rdata
        while (data[iter] != 0) {
            iter++;
        }

        iter += 10; // there are 11 bytes from the last byte of the name to the first of the RData
        return iter;
    }

    /**
     * Gets the domain name
     * @param datagramPacket - DatagramPacket - the response packet
     * @param startOfData - int the offset where the domain name should start
     * @return - String - domain name
     */
    private String getAddressFromData(DatagramPacket datagramPacket, int startOfData) {
        byte[] data = datagramPacket.getData();
        StringBuilder address = new StringBuilder();
        int iter = startOfData;
        byte numBytesToRead = data[iter];
        while (numBytesToRead != 0) {
            if ((numBytesToRead & -64) == -64) {
                iter = (numBytesToRead & 63) << 8 | data[iter + 1];
                numBytesToRead = data[iter];
                continue;
            }
            for (int i = 1; i <= numBytesToRead; i++) {
                address.append((char) data[iter + i]);
            }

            iter += numBytesToRead + 1;
            numBytesToRead = data[iter];
            address.append(".");
        }

        return address.substring(0, address.length() - 1);
    }

    /**
     * Gets random root server
     * @return - InetAddress - the address of a specific root server from root servers list
     * @throws Exception
     */
    private InetAddress getRandomRootServer() throws Exception {
        return InetAddress.getByName((char) ((int) (Math.random() * 13) + 97) + ROOT_SERVER_PATH);
    }

    /**
     * check whether address is not yet reached
     * @param datagramPacket - DatagramPacket - the response packet
     * @return boolean - whether reached or not
     */
    private boolean addressNotReached(DatagramPacket datagramPacket) {
        return isResponseCodeNOERROR(datagramPacket) &&
                isNumOfAnswerRecordsZero(datagramPacket) &&
                isNumOfAuthoritiesGreaterThanZero(datagramPacket);
    }

    /**
     * Checks whether the response code of a response is NOERROR
     * @param datagramPacket - DatagramPacket - response packet
     * @return boolean - according to response code
     */
    private boolean isResponseCodeNOERROR(DatagramPacket datagramPacket) {
        byte[] data = datagramPacket.getData();

        // the location of the error code compared to 0
        return (data[3] & 15) == 0;
    }

    /**
     * Checks whther number of records is zero
     * @param datagramPacket - DatagramPacket - response packet
     * @return boolean
     */
    private boolean isNumOfAnswerRecordsZero(DatagramPacket datagramPacket) {
        byte[] data = datagramPacket.getData();

        return (data[6] << 8 | data[7]) == 0;
    }

    /**
     * Checks whether the number of authorities is greater then zero
     * @param datagramPacket - DatagramPacket - response packet
     * @return boolean
     */
    private boolean isNumOfAuthoritiesGreaterThanZero(DatagramPacket datagramPacket) {
        byte[] data = datagramPacket.getData();

        return (data[8] << 8 | data[9]) > 0;
    }
}