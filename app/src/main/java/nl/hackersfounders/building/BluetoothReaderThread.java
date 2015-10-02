package nl.hackersfounders.building;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Created by Arjan Scherpenisse on 10-7-15.
 */
class BluetoothReaderThread extends Thread {
    private ReaderCallback callback;
    private BluetoothDevice device;
    private InputStream input;

    private static final UUID BT_SERIAL_SSP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    interface ReaderCallback {
        void onTagRead(String tag);
        void onError(Exception reason);
        void log(String message);
    }

    private static final String TAG = "ReaderThread";

    Handler mUI;

    private boolean mRunning = true;

    public BluetoothReaderThread(ReaderCallback callback, BluetoothDevice device) {
        this.callback = callback;
        this.device = device;
        this.mUI = new Handler(Looper.getMainLooper());
    }

    public void terminate() {
        mRunning = false;
    }

    @Override
    public void run() {

        try {
            mUI.post(new Log("Connecting to RFID reader..."));

            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(BT_SERIAL_SSP);
            socket.connect();
            input = socket.getInputStream();

            mUI.post(new Log("Ready for tags"));

            byte tagBuffer[] = new byte[32];
            boolean readingTag = false;
            int tagIdx = 0;

            while (mRunning) {
                byte buffer[] = new byte[16];
                int numBytesRead = input.read(buffer);
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

            input.close();
            socket.close();
            mUI.post(new Log("Stopped RFID reader"));

        } catch (final IOException e) {
            // Deal with error.
            mUI.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(e);
                }
            });
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
