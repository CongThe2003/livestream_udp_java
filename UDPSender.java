
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.Frame;

import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.ByteArrayOutputStream;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Iterator;

public class UDPSender {
    private static final String MULTICAST_ADDRESS = "224.0.0.1";
    private static final int PORT = 8888;
    private static final int BUFFER_SIZE = 8192;
    private static final int FRAME_DELAY = 16;

    private JFrame frame;
    private JButton selectButton;
    private JButton startButton;
    private JLabel statusLabel;
    private String selectedVideoPath;
    private boolean isStreaming = false;
    private Thread streamingThread;

    public UDPSender() {
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        frame = new JFrame("UDP Video Sender");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 150);
        frame.setLayout(new BorderLayout(10, 10));

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());

        selectButton = new JButton("Select Video");
        startButton = new JButton("Start Streaming");
        statusLabel = new JLabel("No video selected");

        startButton.setEnabled(false);

        selectButton.addActionListener(e -> selectVideo());
        startButton.addActionListener(e -> toggleStreaming());

        panel.add(selectButton);
        panel.add(startButton);

        frame.add(panel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void selectVideo() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".mp4");
            }
            public String getDescription() {
                return "MP4 Video Files";
            }
        });

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedVideoPath = fileChooser.getSelectedFile().getAbsolutePath();
            statusLabel.setText("Selected: " + selectedVideoPath);
            startButton.setEnabled(true);
        }
    }

    private void toggleStreaming() {
        if (!isStreaming) {
            startStreaming();
        } else {
            stopStreaming();
        }
    }

    private void startStreaming() {
        isStreaming = true;
        startButton.setText("Stop Streaming");
        selectButton.setEnabled(false);

        streamingThread = new Thread(() -> {
            streamVideo(selectedVideoPath);
        });
        streamingThread.start();
    }

    private void stopStreaming() {
        isStreaming = false;
        startButton.setText("Start Streaming");
        selectButton.setEnabled(true);
        if (streamingThread != null) {
            streamingThread.interrupt();
        }
    }

    private void streamVideo(String videoPath) {
        DatagramSocket socket = null;
        FFmpegFrameGrabber grabber = null;

        try {
            socket = new DatagramSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            grabber = new FFmpegFrameGrabber(videoPath);
            // Thêm các cài đặt để tối ưu grabber
            grabber.setFrameRate(60); // Tăng frame rate
            grabber.setImageWidth(1280);
            grabber.setImageHeight(720);
            grabber.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            grabber.setFormat("mp4");
            grabber.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();

            statusLabel.setText("Streaming: " + videoPath);

            Frame frame;
            while (isStreaming && (frame = grabber.grab()) != null) {
                if (frame.image != null) {
                    BufferedImage image = converter.convert(frame);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();

                    // Tạo frameData từ image
                    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                    ImageWriter writer = writers.next();
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.8f);

                    ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(image, null, null), param);

                    byte[] frameData = baos.toByteArray();

                    // Gửi frame
                    sendFrameData(socket, frameData, group, PORT);

                    // Timing control
                    long frameTime = System.nanoTime();
                    long sleepTime = FRAME_DELAY - (System.nanoTime() - frameTime) / 1_000_000;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }

                    // Cleanup
                    ios.close();
                    writer.dispose();
                    baos.close();
                }
            }
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (grabber != null) {
                    grabber.stop();
                    grabber.release();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                if (isStreaming) {
                    SwingUtilities.invokeLater(this::stopStreaming);
                }
            } catch (Exception e) {
                statusLabel.setText("Error closing resources: " + e.getMessage());
            }
        }
    }
    // Trong UDPSender, thêm header để đánh dấu các gói:
    private static void sendFrameData(DatagramSocket socket, byte[] frameData, InetAddress group, int port) throws Exception {
        int totalPackets = (int) Math.ceil(frameData.length / (double) BUFFER_SIZE);

        for (int i = 0; i < totalPackets; i++) {
            int offset = i * BUFFER_SIZE;
            int packetSize = Math.min(BUFFER_SIZE, frameData.length - offset);

            // Thêm 8 byte header: 4 byte cho packet index và 4 byte cho total packets
            byte[] packetData = new byte[packetSize + 8];

            // Ghi header
            writeInt(packetData, 0, i);
            writeInt(packetData, 4, totalPackets);

            // Copy dữ liệu frame
            System.arraycopy(frameData, offset, packetData, 8, packetSize);

            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, group, port);
            socket.send(packet);
        }
    }

    private static void writeInt(byte[] array, int offset, int value) {
        array[offset] = (byte) (value >> 24);
        array[offset + 1] = (byte) (value >> 16);
        array[offset + 2] = (byte) (value >> 8);
        array[offset + 3] = (byte) value;
    }
    // Existing sendFrameData and writeInt methods remain the same

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UDPSender());
    }
}



