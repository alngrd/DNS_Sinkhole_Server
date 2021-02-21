package il.ac.idc.cs.sinkhole;

public class SinkholeServer {
    private static final int port = 5300;

    /**
     * Program Entry point - checks weather blacklist is Enabled and passes it to the DNSServer
     * @param args
     * @param (optional) path to blacklist file
     * @throws Exception
     */
    public static void main(String[] args) throws Exception
    {
        DNSServer dnsServer = null;
        String path = "";

        if (args.length == 1) {
            path = args[0];
        }

        try {
            dnsServer = new DNSServer(path);
        } catch (Exception e) {
            System.err.println("Error Handling the DNS server");
        }
        dnsServer.listenAndReact();
    }
}
