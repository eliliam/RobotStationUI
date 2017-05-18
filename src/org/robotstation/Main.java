package org.robotstation;
import net.java.games.input.Component;
import org.json.JSONArray;
import org.json.JSONObject;
import org.robotstation.components.CameraStream;
import org.robotstation.components.PersonPanel;
import org.robotstation.configs.*;
import org.robotstation.streaming.MjpegClient;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.List;

import net.java.games.input.*;
import org.robotstation.streaming.newMjpegRunner;
import sun.rmi.runtime.Log;


public class Main extends JPanel {
    private static final Executor executor = Executors.newCachedThreadPool();

    private static CameraStream camera_stream;
    private static JFrame parentFrame = new JFrame("Robot killer");
    private JPanel backgroud = new JPanel();
    private static JLayeredPane frame = new JLayeredPane();
    private JLabel headerLabel;
    private JPanel vidFeed;
    private JPanel ctrlPanel;
    private Random random = new Random();
    private JPanel isSelected;
    private List<JPanel> allRects = new ArrayList<JPanel>();
    private JSONArray allRectsCords = new JSONArray();
    private JButton refreshBtn = new JButton("Refresh");
    private JButton redrawBtn = new JButton("Redraw");
    private JButton connectBtn = new JButton("Connect to server");
    private boolean isConnected = false;
    private Socket[] socket;
    private DataOutputStream toServer;
    private DecimalFormat df = new DecimalFormat("#.###");

    // Set dimension variables
    private int buttonHeight = 80;
    private int headerHeight = 20;

    private void startClient() throws IOException {
        Logger.Log("Starting client");
        socket = new Socket[] {new Socket("172.17.147.1", 34066)};
        Logger.Log("Started client");
        toServer = new DataOutputStream(socket[0].getOutputStream());
        BufferedReader fromServer = new BufferedReader(new InputStreamReader(socket[0].getInputStream()));
        executor.execute(new Thread(){
            @Override
            public void run() {
                while(true) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    JSONArray rectCords = null;
                    if (socket[0] != null) {
                        if (socket[0].isConnected()){
                            connectBtn.setBackground(Color.GREEN);
                            connectBtn.setText("Connected");
                            isConnected = true;
                            try {
                                Logger.Log(fromServer.readLine());
                                try {
                                    rectCords = new JSONArray(fromServer.readLine());
                                } catch (Throwable e) {
                                    rectCords = new JSONArray("[]");
                                }
                                Logger.Log(rectCords.toString());
                            } catch (SocketException ignored) {
                                continue;
                            } catch (Throwable e) {
                                e.printStackTrace();
                                connectBtn.setText("Disconnected");
                                connectBtn.setBackground(Color.RED);
                                socket[0] = null;
                                isConnected = false;
                                break;
                            }
                        }
                    }
                    if (!allRectsCords.toString().equals(rectCords.toString())) {
                        Logger.Log("Rewriting allRectCords");
                        Logger.Log("allRectCords: "+allRectsCords.toString());
                        Logger.Log("rectCords: "+rectCords.toString());
                        allRectsCords = rectCords;
                        clearFrames();
                        for (int i = 0; i < rectCords.length(); i++) {
                            JSONObject tmpRect = rectCords.getJSONObject(i);
                            PersonPanel rect = newRect();
                            rect.findPerson(tmpRect.getInt("x1"), tmpRect.getInt("y1"), tmpRect.getInt("x2"),tmpRect.getInt("y2"));
                            allRects.add(rect);
                            rect.setOpaque(false);
                            frame.add(rect, 10);
                        }


                    } else {
                        Logger.Log("Not rewriting");
                    }
                    Logger.Log("Looping client");
                }
            }
        });

    }

    private void controllerTest() {
        executor.execute(new Thread() {
            public void run() {
                Logger.Log("Starting controller test");
                ControllerEnvironment ce = ControllerEnvironment.getDefaultEnvironment();
                Controller controller = ce.getControllers()[0];
                while (true) {
                    controller.poll();
                    Component[] components = controller.getComponents();
                    for (Component component : components) {
                        if (component.getName() == "rz" && component.getPollData() == 1.0f) {
                            Logger.Log(component.getName() + ": " + component.getPollData());
                        }
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }


    private void controllerInit(){

        executor.execute(new Thread(){
            @Override
            public void run() {
                ControllerEnvironment ce = ControllerEnvironment.getDefaultEnvironment();
                Controller controller = ce.getControllers()[0];
                JSONObject controls = new JSONObject();
                while (true) {
                    controller.poll();
                    Component[] components = controller.getComponents();

                    for (Component component : components) {
                        if (component.getName() == "rz" && component.getPollData() == 1.0f) {
                            controls.put("shoot", 1);
                        } else {
                            controls.put("shoot", 0);
                        }
                        if (Objects.equals(component.getName(), "rx")) {
                            controls.put("x", Float.parseFloat(df.format(component.getPollData())));
                        }
                        if (Objects.equals(component.getName(), "ry")) {

                            controls.put("y", Float.parseFloat(df.format(component.getPollData())));
                        }
                    }
                    try {
                        if (controls.get("shoot") == null || controls.get("shoot") == "") {
                            controls.put("shoot", 0);
                            controls.put("x", 0);
                            controls.put("y", 0);
                        }
                        String toSend = controls.toString();
                        Logger.Log(toSend);
                        toServer.writeBytes(toSend + "\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    // Initializes GUI so that fillGUI() can be ran
    private void initGui() throws InterruptedException {
        parentFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        parentFrame.setResizable(true);
        parentFrame.setUndecorated(true);
        parentFrame.setLayout(new BorderLayout());
        parentFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent windowEvent) {
                System.exit(0);
            }
        });

        frame.setBounds(0,0,parentFrame.getWidth(), parentFrame.getHeight());

        parentFrame.add(frame, BorderLayout.CENTER);

        // Black backgroud
        backgroud.setBackground(Color.BLACK);

        // Label above camera feed
        headerLabel = new JLabel("",JLabel.CENTER);
        headerLabel.setPreferredSize(new Dimension(frame.getWidth(), headerHeight));

        // Bottom panel with buttons
        ctrlPanel = new JPanel();
        ctrlPanel.setLayout(new GridLayout(1,3, 0,0));
        ctrlPanel.setPreferredSize(new Dimension(frame.getWidth(), buttonHeight));

//        parentFrame.add(backgroud, BorderLayout.CENTER);
        parentFrame.add(headerLabel, BorderLayout.NORTH);
        parentFrame.add(ctrlPanel, BorderLayout.SOUTH);

    }

    // Puts content on GUI
    private void fillGUI() throws IOException, InterruptedException {
        headerLabel.setText("Getting vid stream");
        executor.execute(new Thread() {
            @Override
            public void run() {
                while(true) {
                    try {
                        Thread.sleep(1000/15);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try {
                        frame.repaint();
                    } catch(Exception err) {
                        Logger.Log("Failed repainting", true);
                    }
                }
            }
        });

        // What the buttons do
        refreshBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                clearFrames();
            }
        });
        redrawBtn.addActionListener(new ActionListener() {
//            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                PersonPanel toAdd = newRect();
                toAdd.findPerson(random.nextInt(frame.getWidth() - 200), random.nextInt(frame.getHeight() - 500) + 100, 200, 200);
                frame.add(toAdd);
                allRects.add(toAdd);
            }
        });
        connectBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    if (isConnected) {
                        socket[0].close();
                        socket[0] = null;
                    }
//                    controllerTest();
                    startClient();
                    controllerInit();
//                    MjpegClient client = new MjpegClient("http://172.17.146.105:9090/_stream/stream_0/1957747793");
//                    MjpegClient client = new MjpegClient("http://localhost:8081");
                    Thread.sleep(300);
//                    camera_stream = new CameraStream(client);
//                    client.start();
//                    camera_stream.setSize(0,0,500,500);
//                    frame.add(camera_stream, 0);
                    parentFrame.pack();

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        connectBtn.setBackground(Color.RED);

        // Adding buttons to bottom panel
        ctrlPanel.add(refreshBtn);
        ctrlPanel.add(redrawBtn);
        ctrlPanel.add(connectBtn);
    }

    private void clearFrames(){
        for (int i = 0; i < allRects.size(); i++){
            frame.remove(allRects.get(i));
        }
        allRects.clear();
    }

    // Custom JPanel object that is designed to surround a person
    private PersonPanel newRect(){
        PersonPanel tempR = new PersonPanel();

        tempR.setBorder(BorderFactory.createMatteBorder(5,5,5,5,Color.RED));
        tempR.setOpaque(false);

        tempR.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isSelected != tempR ) {
                    if (isSelected != null){
                        isSelected.setBorder(BorderFactory.createMatteBorder(5,5,5,5,Color.RED));
                    }
                    Logger.Log("Ran");
                    isSelected = tempR;
                    tempR.setBorder(BorderFactory.createMatteBorder(5,5,5,5,Color.BLUE));
                }
            }
        });

        allRects.add(tempR);
        return tempR;
    }

    private Main() throws InterruptedException, IOException {
        Logger.init();
        Resources.init();
        try {
            Configs.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        initGui();
        fillGUI();
        Logger.Log("Packing...");
        parentFrame.pack();
        Logger.Log("Packed!");
        parentFrame.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        parentFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Logger.init();
        new Main();
    }
}