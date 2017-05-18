package org.robotstation.streaming;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.*;
import java.awt.event.*;

import javax.imageio.ImageIO;

/**
 * Given an extended JPanel and URL read and create BufferedImages to be displayed from a MJPEG stream
 * @author shrub34 Copyright 2012
 * Free for reuse, just please give me a credit if it is for a redistributed package
 */
public class newMjpegRunner extends JPanel implements Runnable
{
    private static final String CONTENT_LENGTH = "Content-Length: ";
    private static final String TIMESTAMP = "X-Timestamp: ";
    private JLabel l1;
    private InputStream urlStream;
    private StringWriter stringWriter;
    private boolean processing = true;
    private BufferedImage image2;
    private String username = "viktor";
    private String password = "viktor";
    private int fps;
    private Timer fpsTimer;

    ActionListener fpsReader = new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
            l1.setText("FPS: " + fps);
            fps=0;
        }
    };

    public newMjpegRunner(URL url) throws IOException
    {
        fpsTimer = new Timer(1000,fpsReader); //check number of pictures displayed every 1000 ms
        fpsTimer.start();
        fps = 0;
        URLConnection urlConn = url.openConnection();
//        String userpass = username + ":" + password;
//        String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());

//        urlConn.setRequestProperty ("Authorization", basicAuth);
        urlConn.setReadTimeout(2000); //wait 2 sec to connect, otherwise timeout
        urlConn.connect();
        urlStream = urlConn.getInputStream();
        stringWriter = new StringWriter(128);
        CreateGUI();
    }
    public void CreateGUI(){
        l1 = new JLabel("FPS: ");
        this.setSize(320,240);
        this.add(l1);
        this.setLayout(new GridLayout(1,1));
    }

    /**
     * Stop the loop, and allow it to clean up
     */
    public synchronized void stop()
    {
        processing = false;
    }

    public void paintComponent(Graphics g){ //invoked by repaint
        super.paintComponent(g);
        if(image2 != null){
            g.drawImage(image2, 0, 0, this);
        }
    }
    /**
     * Keeps running while process() returns true
     *
     * Each loop asks for the next JPEG image and then sends it to our JPanel to draw
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run()
    {
        while(processing)
        {
            try
            {
                byte[] imageBytes = retrieveNextImage();
                ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                image2 = ImageIO.read(bais);
                //System.out.println(image2);
                this.repaint(); //repaint the panel
                fps++; //a picture has been painted, increase fps

            }catch(SocketTimeoutException ste){
                System.err.println("MjpegRunner error: " + ste);
                stop();
            }catch(IOException e){
                System.err.println("MjpegRunner read: " + e);
                stop();
            }
        }
        // close streams
        try
        {
            urlStream.close();
        }catch(IOException ioe){
            System.err.println("Failed to close the stream: " + ioe);
        }
    }

    /**
     * Using the <i>urlStream</i> get the next JPEG image as a byte[]
     * @return byte[] of the JPEG
     * @throws IOException
     */
    private byte[] retrieveNextImage() throws IOException
    {
        boolean haveHeader = false;
        int currByte = -1;

        String header = null;
        int i = 0;
        while((currByte = urlStream.read()) > -1 && !haveHeader) //read bytes until header is found
        {
            stringWriter.write(currByte); //read bytes as string

            String tempString = stringWriter.toString();
            int indexOf = tempString.indexOf(TIMESTAMP); //make sure to get content length in the header
            if(indexOf > 0)
            {
                haveHeader = true;
                header = tempString; //save the header(or atleast the important parts of it)

            }


        }

        // 255 indicates the start of the jpeg image data
        while((urlStream.read()) != 255)
        {
            //read the rest of the header data
        }

        // rest is the buffer
        int contentLength = contentLength(header);

        byte[] imageBytes = new byte[contentLength + 1];
        // since we ate the original 255 , shove it back in

        imageBytes[0] = (byte)255;
        int offset = 1;
        int numRead = 0;
        while (offset < imageBytes.length
                && (numRead=urlStream.read(imageBytes, offset, imageBytes.length-offset)) >= 0) {
            offset += numRead;
        }

        stringWriter = new StringWriter(128);

        return imageBytes;
    }

    // dirty but it works content-length parsing
    private static int contentLength(String header)
    {

        int indexOfContentLength = header.indexOf(CONTENT_LENGTH);

        //System.out.println(indexOfContentLength + "asdasd" );
        //System.out.println(header);
        int valueStartPos = indexOfContentLength + CONTENT_LENGTH.length();
        int indexOfEOL = header.indexOf('\n', indexOfContentLength);

        String lengthValStr = header.substring(valueStartPos, indexOfEOL).trim();

        int retValue = Integer.parseInt(lengthValStr);

        return retValue;
    }
}