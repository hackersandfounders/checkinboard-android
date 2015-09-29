package nl.hackersfounders.building;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class MainActivity extends Activity implements ReaderThread.ReaderCallback {

    private UsbDeviceConnection mUsbConnection;
    private ReaderThread mReaderThread;
    private static final String ACTION_USB_PERMISSION =
            "com.hackersandfounders.USB_PERMISSION";

    private PendingIntent mPermissionIntent;

    @InjectView(R.id.webView)
    WebView webView;

    private UsbManager mUsbManager;
    private UsbSerialDriver mDriver;
    private MediaPlayer mMediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        ButterKnife.inject(this);

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setVolume(1f, 1f);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
//        webSettings.setSaveFormData(true);
//        webSettings.setDomStorageEnabled(true);
//        webSettings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new BoardFeedbackInterface(), "BoardFeedback");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });

        WebView.setWebContentsDebuggingEnabled(true);

        webView.loadUrl(BuildConfig.WEBSITE);

        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectUSB();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mReaderThread != null) {
            mReaderThread.terminate();
        }
    }

    private void connectUSB() {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        if (availableDrivers.isEmpty()) {
            finishError("No USB device present");
            return;
        }

        // Open a connection to the first available driver.
        mDriver = availableDrivers.get(0);
        UsbDeviceConnection mUsbConnection = mUsbManager.openDevice(mDriver.getDevice());
        if (mUsbConnection == null) {
            mUsbManager.requestPermission(mDriver.getDevice(), mPermissionIntent);
            return;
        }

        startReaderThread(mUsbConnection);
    }

    private void startReaderThread(UsbDeviceConnection connection) {
        mReaderThread = new ReaderThread(this, mDriver, connection);
        mReaderThread.start();
    }

    private void finishError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        //finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onTagRead(final String tag) {

        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("javascript:tag('" + tag + "')");
            }
        });

    }

    @Override
    public void log(String message) {
        String s = "" + System.currentTimeMillis() + ": " + message;
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            startReaderThread(mUsbManager.openDevice(device));
                        }
                    } else {
                        log("Permission denied for device " + device);
                    }
                }
            }
        }
    };


    private class BoardFeedbackInterface {

        @JavascriptInterface
        public void playCheckinSound() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log("Play checkin sound");
                    playSample(R.raw.sign_in);
                }
            });
        }

        @JavascriptInterface
        public void playCheckoutSound() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log("Play checkout sound");
                    playSample(R.raw.sign_out);
                }
            });
        }
    }


    private void playSample(int resid) {
        AssetFileDescriptor afd = getResources().openRawResourceFd(resid);
        try {
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            afd.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
