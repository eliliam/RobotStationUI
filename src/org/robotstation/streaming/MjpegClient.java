package org.robotstation.streaming;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.robotstation.Main;
import org.robotstation.configs.Logger;

public class MjpegClient implements Runnable {

    private final String MJPEG_PREFIX = "multipart/x-mixed-replace;boundary=";
    private final String MJPEG_LENGHT_KEY = "Content-Length";
    private final byte[] START_MARKER = {(byte) 0xFF, (byte) 0xD8};
    private final byte[] END_MARKER = {(byte) 0xFF, (byte) 0xD9};
    private final static int MAX_HEADER_LENGTH = 100;
    private final static int MAX_FRAME_LENGTH = 40000 + MAX_HEADER_LENGTH;

    private String boundary;
    private String url;
    private boolean running;
    private Callback onNewFrameCallback;

    public interface Callback {
        public void onNewFrame(BufferedImage bitmap);
        public void onDisconnect();
        public void onConnect();
    }

    public MjpegClient(String url) {
        this.url = url;
        this.running = false;
        onNewFrameCallback = null;
    }

    public void start() {
        this.running = true;
        Thread mjpeg_thread = new Thread(this, "MjpegClient");
        mjpeg_thread.setDaemon(true);
        mjpeg_thread.start();
    }

    public void stop() {
        this.running = false;
    }

    public void setFrameHandler(Callback callback) {
        this.onNewFrameCallback = callback;
    }

    private int getEndOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int seqIndex = 0;
        byte c;
        for(int ind = 0; ind < MAX_FRAME_LENGTH; ind++) {
            c = (byte) in.readUnsignedByte();
            if(c == sequence[seqIndex]) {
                seqIndex++;
                if(seqIndex == sequence.length) return ind + 1;
            } else seqIndex = 0;
        }

        return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int end = this.getEndOfSequence(in, sequence);
        return (end < 0) ? -1 : (end - sequence.length);
    }

    private int parseContentLength(byte[] header) throws IOException, NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(header);
        Properties props = new Properties();
        props.load(headerIn);

        return Integer.parseInt(props.getProperty(this.MJPEG_LENGHT_KEY));
    }

    public BufferedImage readFrame(DataInputStream in) throws IOException {
        int content_length = 0;

        in.mark(MAX_FRAME_LENGTH);
        int header_length = this.getStartOfSequence(in, this.START_MARKER);
        in.reset();
        byte[] header = new byte[header_length];
        in.readFully(header);
        try {
            content_length = this.parseContentLength(header);
        } catch (NumberFormatException ignore) {
            content_length = this.getEndOfSequence(in, this.END_MARKER);
        }
        in.reset();
        byte[] frameData = new byte[content_length];
        in.skipBytes(header_length);
        in.readFully(frameData);

        return ImageIO.read(new ByteArrayInputStream(frameData));
    }

    public void run() {
        Logger.Log("Starting mjpeg client " + this.url);
        while(this.running) {
            try {
                if(this.onNewFrameCallback != null) {
                    this.onNewFrameCallback.onDisconnect();
                }

                URI uri = URI.create(this.url);
                DefaultHttpClient http_client = new DefaultHttpClient();

                HttpResponse http_response = null;
                try {
                    Logger.Log("Connecting to " + this.url);
                    http_response = http_client.execute(new HttpGet(uri));
                } catch (IOException ignored) {
                    Logger.Log("Failed connecting to " + this.url, true);
                    Thread.sleep(300);
                    continue;
                }

                HttpEntity http_entity = http_response.getEntity();
                String content_type = http_entity.getContentType().getValue().replaceAll("\\s", "");
                if(!content_type .startsWith(MJPEG_PREFIX)) {
                    Logger.Log("This is not an Mjpeg stream " + this.url, true);
                    Logger.Log("Content type " + content_type);
                    Thread.sleep(300);
                    continue;
                }

                this.boundary = content_type.substring(MJPEG_PREFIX.length());
                Logger.Log("Found the Mjpeg Boundary " + this.boundary);


                BufferedInputStream in = null;

                try {
                    in = new BufferedInputStream(http_entity.getContent(), MAX_FRAME_LENGTH);
                } catch(IOException ignored) {
                    Logger.Log("Couldn't get the Http stream from " + this.url);
                    Thread.sleep(300);
                    continue;
                }

                DataInputStream mjpeg = new DataInputStream(in);

                if(this.onNewFrameCallback != null) {
                    this.onNewFrameCallback.onConnect();
                }

                Logger.Log("Starting the Mjpeg frame loop");
                while(this.running) {
                    BufferedImage current_frame = null;

                    try {
                        current_frame = this.readFrame(mjpeg);
                    } catch(IOException ignored) {
                        Logger.Log("Failed reading frame from mjpeg", true);
                        Logger.Log("Probably recieved empty frame from Mjpeg " + this.url);
                        break;
                    }

                    if(this.onNewFrameCallback != null) {
                        this.onNewFrameCallback.onNewFrame(current_frame);
                    }

                }

                try {
                    Logger.Log("Closing the Mjpeg stream " + this.url);
                    mjpeg.close();
                    if(this.onNewFrameCallback != null) {
                        this.onNewFrameCallback.onDisconnect();
                    }
                } catch(IOException ignored) {

                }
            } catch (Exception err) {
                err.printStackTrace();
                Logger.Log("Failed to connect or read from Mjpeg stream, retrying...", true);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}