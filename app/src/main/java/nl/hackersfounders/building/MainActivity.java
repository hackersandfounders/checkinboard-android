package nl.hackersfounders.building;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
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

import java.util.Set;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.OnLongClick;


public class MainActivity extends Activity implements BluetoothReaderThread.ReaderCallback {

    private static final String DEVICE_NAME = "HC-06";

    private BluetoothReaderThread mReaderThread;

    @InjectView(R.id.webView)
    WebView webView;

    @InjectView(R.id.errorBar)
    View errorBar;

    private MediaPlayer mMediaPlayer;
    private BluetoothAdapter mBlueAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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

        goFullscreen();

        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mgr.setStreamVolume(AudioManager.STREAM_NOTIFICATION, mgr.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0);

        connectBluetooth();
    }

    @OnLongClick(R.id.refreshButton)
    public boolean onRefreshClick() {
        webView.loadUrl(BuildConfig.WEBSITE);
        return true;
    }

    protected void goFullscreen() {
        // Full screen
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    protected void onResume() {
        super.onResume();
        goFullscreen();
    }

    @Override
    protected void onDestroy() {
        if (mReaderThread != null) {
            mReaderThread.terminate();
            mReaderThread = null;
        }

        super.onDestroy();
    }

    private void connectBluetooth() {

        errorBar.setVisibility(View.GONE);
        mBlueAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> paired = mBlueAdapter.getBondedDevices();
        for (BluetoothDevice device : paired) {
            if (DEVICE_NAME.equals(device.getName())) {
                mReaderThread = new BluetoothReaderThread(this, device);
                mReaderThread.start();
            }
        }
    }

    @OnClick(R.id.retryButton)
    public void onRetry() {
        connectBluetooth();
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
    public void onError(Exception e) {
        Toast.makeText(MainActivity.this, "Bluetooth connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        errorBar.setVisibility(View.VISIBLE);
        mReaderThread = null;
    }

    @Override
    public void log(String message) {
        String s = "" + System.currentTimeMillis() + ": " + message;
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }


    private class BoardFeedbackInterface {

        @JavascriptInterface
        public void playCheckinSound() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //log("Play checkin sound");
                    playSample(R.raw.sign_in);
                    //playNotificationSound(MainActivity.this);
                }
            });
        }

        @JavascriptInterface
        public void playCheckoutSound() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //log("Play checkout sound");
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
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            mMediaPlayer.setVolume(1f, 1f);
            afd.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void playNotificationSound(Context context) {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(context, notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
