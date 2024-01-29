package org.example;

import com.pi4j.io.gpio.*;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.server.Server;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RoverController {

    private static final int ENABLE_A = 14;
    private static final int INPUT_1 = 15;
    private static final int INPUT_2 = 13;
    private static final int INPUT_3 = 2;
    private static final int INPUT_4 = 0;
    private static final int ENABLE_B = 12;
    private static final int BATTERY_PIN = 0;
    private static final int ROVER_HEADLIGHTS = 5;
    private static final int ROVER_SPEED = 220;
    private static final int ROVER_FW_BW_SPEED = 135;
    private static final int ROVER_LR_SPEED = 200;
    private static final int TURN_COEFFICIENT = 45;
    private static boolean isColorDetected = false;
    private static String colorDetected = "NC";
    private static int roverHeadlights = 5;
    private static double batteryVoltCoef = 1.98;
    private static double cutoffVolt = 5.5;
    private static double batteryVolt = 7.2;
    private static double actualVoltage = 0.0;
    private static int batteryPercent = 0;
    private static boolean isGameStarted = false;
    private static String roverConnMsg = "Rover Connected";
    private static String connected = "";
    private static int wsNum = 0;
    private static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static GpioController gpioController;
    private static GpioPinPwmOutput enableAPin;
    private static GpioPinDigitalOutput input1Pin;
    private static GpioPinDigitalOutput input2Pin;
    private static GpioPinDigitalOutput input3Pin;
    private static GpioPinDigitalOutput input4Pin;
    private static GpioPinPwmOutput enableBPin;
    private static GpioPinDigitalOutput roverHeadlightsPin;
    private static final int SENSOR_PIN = 1; // Replace with the actual sensor pin

    @ServerEndpoint("/rover")
    public static class RoverWebSocketServer {

        @OnOpen
        public void onOpen(Session session) {
            wsNum++;
            batteryPercent = getBatteryVoltage();
            connected = roverConnMsg + "|" + actualVoltage + "|" + batteryPercent;
            try {
                session.getBasicRemote().sendText(connected);
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("<WSC>");
        }

        @OnMessage
        public void onMessage(Session session, String message) {
            if (message.equals("1") || message.equals("4")) {
                isGameStarted = true;
                System.out.println("<GS>");
            } else if (message.equals("3")) {
                isGameStarted = true;
                System.out.println("<GM>");
            } else if (message.equals("2")) {
                isGameStarted = true;
                System.out.println("<PH>");
            } else if (message.equals("F")) {
                moveForward();
            } else if (message.equals("B")) {
                moveBackward();
            } else if (message.equals("L")) {
                moveLeft();
            } else if (message.equals("R")) {
                moveRight();
            } else if (message.equals("S")) {
                stopRover();
            }
        }

        @OnClose
        public void onClose(Session session) {
            System.out.println("<GE>");
            stopRover();
            isGameStarted = false;
        }
    }

    public static void main(String[] args) throws DeploymentException {
        setup();
        executorService.scheduleAtFixedRate(RoverController::loop, 0, 100, TimeUnit.MILLISECONDS);
    }

    private static void setup() throws DeploymentException {
        gpioController = GpioFactory.getInstance();
        enableAPin = gpioController.provisionSoftPwmOutputPin(RaspiPin.GPIO_14);
        input1Pin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_15);
        input2Pin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_13);
        input3Pin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_02);
        input4Pin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_00);
        enableBPin = gpioController.provisionSoftPwmOutputPin(RaspiPin.GPIO_12);
        roverHeadlightsPin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_05, PinState.LOW);

        int count = 0;
        while (count < 17 && !isWifiConnected()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            count++;
        }

        if (!isWifiConnected()) {
            int retryInterval = 5000; // 5 seconds
            while (true) {
                System.err.println("WiFi connection failed. Retrying in " + retryInterval / 1000 + " seconds...");

                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (isWifiConnected()) {
                    System.out.println("WiFi reconnected.");
                    break;
                }
            }
        }

        System.out.println("<C>");

        ServerEndpointConfig sec = ServerEndpointConfig.Builder.create(RoverWebSocketServer.class, "/rover").build();
        Server server = new Server("localhost", 5045, "/", (Map<String, Object>) null, (Set<Class<?>>) sec);
        server.start();
    }

    private static void loop() {

        System.out.println("Periodic tasks in the loop...");

        try {
            // Check and update sensor values
            int sensorValue = readSensor(); // You need to implement readSensor() method
            System.out.println("Sensor value: " + sensorValue);

            // Check battery voltage
            int batteryPercentage = getBatteryVoltage();
            System.out.println("Battery Percentage: " + batteryPercentage);

            // Perform additional tasks based on sensor values
            if (sensorValue > 100) {
                System.out.println("Sensor value is above threshold. Taking action...");

                // Perform action based on the sensor value
                //performActionBasedOnSensor(sensorValue);
            }

            // Update Rover status
            //updateRoverStatus();

            // Check WiFi connectivity
            if (isWifiConnected()) {
                System.out.println("WiFi is connected.");
            } else {
                System.out.println("WiFi is not connected. Attempting reconnection...");
            }


        } catch (Exception e) {
            // Handle exceptions that might occur during periodic tasks
            e.printStackTrace();
        }
    }


    private static boolean isWifiConnected() {

        boolean isConnected = pingServer("www.google.com"); // You need to implement pingServer() method
        return isConnected;
    }

    private static int readSensor() {

        //int sensorValue = AnalogInput.create(SENSOR_PIN).read();
        return 1; // return sensorValue
    }

    private static boolean pingServer(String serverAddress) {

        try {
            InetAddress address = InetAddress.getByName(serverAddress);
            return address.isReachable(5000); // 5000 milliseconds timeout
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void moveForward() {
        if (isGameStarted) {
            enableAPin.setPwm(ROVER_SPEED);
            enableBPin.setPwm(ROVER_SPEED);
            input1Pin.high();
            input2Pin.low();
            input3Pin.high();
            input4Pin.low();
        }
    }

    private static void moveBackward() {
        if (isGameStarted) {
            enableAPin.setPwm(ROVER_FW_BW_SPEED);
            enableBPin.setPwm(ROVER_FW_BW_SPEED);
            input1Pin.low();
            input2Pin.high();
            input3Pin.low();
            input4Pin.high();
        }
    }

    private static void moveRight() {
        if (isGameStarted) {
            enableAPin.setPwm(ROVER_LR_SPEED);
            enableBPin.setPwm(ROVER_LR_SPEED + TURN_COEFFICIENT);
            input1Pin.high();
            input2Pin.low();
            input3Pin.low();
            input4Pin.high();
        }
    }

    private static void moveLeft() {
        if (isGameStarted) {
            enableAPin.setPwm(ROVER_LR_SPEED + TURN_COEFFICIENT);
            enableBPin.setPwm(ROVER_LR_SPEED);
            input1Pin.low();
            input2Pin.high();
            input3Pin.high();
            input4Pin.low();
        }
    }

    private static void stopRover() {
        if (isGameStarted) {
            enableAPin.setPwm(0);
            enableBPin.setPwm(0);
            input1Pin.low();
            input2Pin.low();
            input3Pin.low();
            input4Pin.low();
        }
    }

    private static int getBatteryVoltage() {
        //int adcValue = AnalogInput.create(BATTERY_PIN).read();
        //double voltage = adcValue * batteryVoltCoef;
        //actualVoltage = voltage;
        //batteryPercent = (int) ((voltage / cutoffVolt) * 100);
        return batteryPercent;
    }
}