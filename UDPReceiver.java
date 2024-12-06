import java.net.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;public class UDPReceiver {
    private static final String MULTICAST_ADDRESS = "224.0.0.1";
    private static final int PORT = 8888;
    private static final int BUFFER_SIZE = 65535;
    private static final int MAX_FRAME_SIZE = 1000000;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Video Stream Receiver");
        JLabel videoLabel = new JLabel();
        frame.setLayout(new BorderLayout());
        frame.add(videoLabel, BorderLayout.CENTER);
        frame.setSize(1280, 720);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        try {
            // New network configuration
            MulticastSocket socket = new MulticastSocket(PORT);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(
                InetAddress.getByName("192.168.34.253")); // Your IP address
            socket.setNetworkInterface(networkInterface);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(new InetSocketAddress(group, PORT), networkInterface);

            // Debug information
            System.out.println("Listening on: " + MULTICAST_ADDRESS + ":" + PORT);
            System.out.println("Network Interface: " + networkInterface.getDisplayName());

            byte[] buffer = new byte[BUFFER_SIZE];
            ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
            int expectedPacket = 0;
            int totalPackets = -1;

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                System.out.println("Received packet size: " + packet.getLength());

                byte[] data = packet.getData();
                int packetIndex = readInt(data, 0);
                int packetTotal = readInt(data, 4);

                if (packetIndex == 0) {
                    frameBuffer.reset();
                    expectedPacket = 0;
                    totalPackets = packetTotal;
                }

                if (packetIndex == expectedPacket) {
                    frameBuffer.write(data, 8, packet.getLength() - 8);
                    expectedPacket++;

                    if (expectedPacket == totalPackets) {
                        byte[] frameData = frameBuffer.toByteArray();
                        try {
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameData));
                            if (image != null) {
                                SwingUtilities.invokeLater(() -> {
                                    Image scaledImage = image.getScaledInstance(
                                            frame.getWidth(),
                                            frame.getHeight(),
                                            Image.SCALE_SMOOTH
                                    );
                                    videoLabel.setIcon(new ImageIcon(scaledImage));
                                    frame.repaint();
                                });
                            }
                        } catch (IOException e) {
                            System.out.println("Error processing frame");
                        }
                        frameBuffer.reset();
                        expectedPacket = 0;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int readInt(byte[] array, int offset) {
        return ((array[offset] & 0xFF) << 24) |
                ((array[offset + 1] & 0xFF) << 16) |
                ((array[offset + 2] & 0xFF) << 8) |
                (array[offset + 3] & 0xFF);
    }
}
