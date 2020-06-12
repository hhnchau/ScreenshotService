package m.hanet.forcegoundservice;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;

public class MainActivity extends AppCompatActivity {


    Messenger mService = null;

    boolean mBound;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            mBound = false;
        }
    };

    public void sayHello(View v) {
        if (!mBound) return;
        Message msg = Message.obtain(null, ScreenShotService.SCREEN_SHOT, 0, 0);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        new CountDownTimer(60000000, 20000) {

            @Override
            public void onTick(long millisUntilFinished) {
                sayHello(null);
            }

            @Override
            public void onFinish() {

            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void click(View view) {
        //sayHello(view);
        //moveTaskToBack(true);
        startScreenShot();
    }


    private void startScreenShot() {
        final MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager != null) {
            final Intent permissionIntent = manager.createScreenCaptureIntent();
            startActivityForResult(permissionIntent, 1000);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        startScreenShotService(resultCode, intent);
    }

    private void startScreenShotService(final int resultCode, final Intent intent) {
        if (intent != null) {
            final Intent i = new Intent(this, ScreenShotService.class);
            i.setAction(ScreenShotService.ACTION_INIT);
            i.putExtra("key", resultCode);
            i.putExtras(intent);
            bindService(i, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

}
