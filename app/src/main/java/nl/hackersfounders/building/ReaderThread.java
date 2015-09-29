package nl.hackersfounders.building;

import android.hardware.usb.UsbDeviceConnection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

/**
 * Created by Arjan Scherpenisse on 10-7-15.
 */
class ReaderThread extends Thread {
    private ReaderCallback callback;

    interface ReaderCallback {
        void onTagRead(String tag);

        void log(String message);
    }

    private static final String TAG = "ReaderThread";
    Handler mUI;
    private UsbSerialDriver driver;
    private UsbDeviceConnection connection;
    private boolean mRunning = true;

    public ReaderThread(ReaderCallback callback, UsbSerialDriver driver, UsbDeviceConnection connection) {
        this.callback = callback;
        this.mUI = new Handler(Looper.getMainLooper());
        this.driver = driver;
        this.connection = connection;
    }

    public void terminate() {
        mRunning = false;
    }

    @Override
    public void run() {

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(9600, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            mUI.post(new Log("Starting read loop"));

            byte tagBuffer[] = new byte[32];
            boolean readingTag = false;
            int tagIdx = 0;

            while (mRunning) {
                byte buffer[] = new byte[16];
                int numBytesRead = port.read(buffer, 1000);
                for (int i=0; i<numBytesRead; i++) {
                    if (!readingTag) {
                        if (buffer[i] == 2) {
                            // tag start
                            tagIdx = 0;
                            readingTag = true;
                        } else {
                            // skip
                        }
                    } else {
                        // reading tag
                        if (buffer[i] == 3) {
                            // stop tag
                            readingTag = false;
                            final String tag = new String(tagBuffer, 0, tagIdx).trim();
                            mUI.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onTagRead(tag);
                                }
                            });
                            //mUI.post(new Log("Got tag! length = " + (tagIdx) + " " + bytesToHex(tagBuffer)));

                        } else {
                            tagBuffer[tagIdx++] = buffer[i];
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Deal with error.
        } finally {
            try {
                port.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class Log implements Runnable {
        private String message;

        public Log(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            callback.log(message);
        }
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}
