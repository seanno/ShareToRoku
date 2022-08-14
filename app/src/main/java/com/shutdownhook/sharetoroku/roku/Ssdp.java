package com.shutdownhook.sharetoroku.roku;

import com.shutdownhook.sharetoroku.util.Loggy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class Ssdp {

    private final static String ADDRESS = "239.255.255.250";
    private final static int PORT = 1900;
    private final static int BUFFER_SIZE_KB = 10;

    private final static String PROBE_MSG_FMT =
        "M-SEARCH * HTTP/1.1\r\n" +
        "Host: %s:%d\r\n" +
        "Man: \"ssdp:discover\"\r\n" +
        "ST: %s\r\n" +
        "Mx: %d\r\n" +
        "\r\n";

    public interface Callback {
        public void handle(Map<String,String> msg);
    }

    public static void probe(String st, int timeoutSeconds, Callback callback)
            throws IOException {

        MulticastSocket sock = null;

        try {
            sock = new MulticastSocket();
            sock.setSoTimeout(500);
            sock.setReuseAddress(true);

            byte[] rgb = new byte[BUFFER_SIZE_KB * 1024];
            DatagramPacket dgram = new DatagramPacket(rgb, rgb.length);

            sendProbe(sock, st, timeoutSeconds - 1);
            Instant finishTime = Instant.now().plusSeconds(timeoutSeconds);

            while (Instant.now().isBefore(finishTime)) {

                Arrays.fill(rgb, (byte) 0);
                try {
                    sock.receive(dgram);
                    handle(dgram, st, callback);
                }
                catch (SocketTimeoutException e) {
                    continue;
                }
            }
        }
        finally {
            if (sock != null) sock.close();
        }
    }

    private static void handle(DatagramPacket dgram, String st, Callback callback) {

        String msg = new String(dgram.getData(), StandardCharsets.UTF_8).trim();
        String[] lines = msg.split("\r\n");

        if (!lines[0].toLowerCase().startsWith("http")) {
            log.i("Skipping SSDP response starting with: %s", lines[0]);
            return;
        }

        Map<String, String> parts = new HashMap<String, String>();
        for (int i = 1; i < lines.length; ++i) {

            int ichColon = lines[i].indexOf(":");
            if (ichColon == -1) continue;

            String n = lines[i].substring(0, ichColon).trim().toLowerCase();
            String v = lines[i].substring(ichColon + 1).trim();
            parts.put(n,v);
        }

        if (!st.equalsIgnoreCase("ssdp:all")) {
            String foundSt = parts.get("st");
            if (foundSt == null) foundSt = parts.get("nt");
            if (!st.equalsIgnoreCase(foundSt)) {
                log.i("Skipping SSDP response; %s != %s", foundSt, st);
                return;
            }
        }

        callback.handle(parts);
    }

    private static void sendProbe(MulticastSocket sock, String st, int timeoutSeconds)
            throws UnknownHostException, IOException {

        String msg = String.format(PROBE_MSG_FMT, ADDRESS, PORT, st, timeoutSeconds);
        byte[] rgb = msg.getBytes(StandardCharsets.UTF_8);

        DatagramPacket dgram = new DatagramPacket(rgb, rgb.length,
                InetAddress.getByName(ADDRESS), PORT);

        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            if (iface.isLoopback() || !iface.isUp() || !iface.supportsMulticast()) continue;

            try {
                sock.setNetworkInterface(iface);
                sock.send(dgram);
            }
            catch (SocketException e) {
                // log but ignore
                log.w("ex sending ssdp probe: %s", e.toString());
            }
        }
    }

    private final static Loggy log = new Loggy(Ssdp.class.getName());
}
