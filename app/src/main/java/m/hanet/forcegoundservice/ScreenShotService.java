package m.hanet.forcegoundservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class ScreenShotService extends Service {
    private static final String TAG = "ScreenShot";
    public static final String ACTION_INIT = "INIT";
    public static final int SCREEN_SHOT = 1;

    private static class MessageHandler extends Handler {
        WeakReference<ScreenShotService> mService;

        MessageHandler(ScreenShotService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            ScreenShotService service = mService.get();
            if (msg.what == SCREEN_SHOT) {
                Toast.makeText(service, "Screen Shot!", Toast.LENGTH_SHORT).show();
                service.shotScreen();
            }
        }
    }

    final Messenger mMessenger = new Messenger(new MessageHandler(this));

    @Override
    public IBinder onBind(Intent intent) {
        initScreenShot(intent);
        return mMessenger.getBinder();
    }

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private ImageReader.OnImageAvailableListener mImageListener;
    private MediaScannerConnection.OnScanCompletedListener mScanListener;

    @Override
    public void onCreate() {
        super.onCreate();
        deleteFolder();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Toast.makeText(this, "onCreate", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseScreenShot();
    }

    private void initScreenShot(Intent intent) {
        final int resultCode = intent.getIntExtra("key", 0);
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);
    }

    private void shotScreen() {
        getScreenShot()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .map(new Function<Image, Bitmap>() {
                    @Override
                    public Bitmap apply(@NonNull Image image) {
                        return createBitmap(image);
                    }
                })
                .zipWith(createFile(), new BiFunction<Bitmap, String, String>() {
                    @Override
                    public String apply(@NonNull Bitmap bitmap, @NonNull String fileName) throws Exception {
                        writeFile(bitmap, fileName);
                        return fileName;
                    }
                })
                .flatMap(new Function<String, Publisher<String>>() {
                    @Override
                    public Publisher<String> apply(@NonNull String fileName) {
                        return updateScan(fileName);
                    }
                })
                .observeOn(Schedulers.io())
                .doFinally(new Action() {
                    @Override
                    public void run() {
                        Log.d(TAG, "check do finally: " + Thread.currentThread().toString());
                        //releaseScreenShot();
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(String filename) {
                        Log.d(TAG, "onNext: " + filename);
                        //Toast.makeText(ScreenShotService.this, "Success: " + filename, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.w(TAG, "onError: ", t);
                        //Toast.makeText(ScreenShotService.this, "Error: " + t, Toast.LENGTH_SHORT).show();

                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");
                    }
                });
    }

    private Bitmap createBitmap(Image image) {
        Log.d(TAG, "check create bitmap: " + Thread.currentThread().toString());
        Bitmap bitmap;
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        // create bitmap
        bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        return bitmap;
    }

    private Flowable<Image> getScreenShot() {
        final Point screenSize = new Point();
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = window.getDefaultDisplay();
        display.getRealSize(screenSize);
        return Flowable.create(new FlowableOnSubscribe<Image>() {
            @Override
            public void subscribe(@NonNull final FlowableEmitter<Image> emitter) throws Exception {
                mImageReader = ImageReader.newInstance(screenSize.x, screenSize.y, PixelFormat.RGBA_8888, 2);
                mVirtualDisplay = mediaProjection.createVirtualDisplay("cap", screenSize.x, screenSize.y, metrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mImageReader.getSurface(), null, null);
                mImageListener = new ImageReader.OnImageAvailableListener() {
                    Image image = null;

                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        try {
                            image = imageReader.acquireLatestImage();
                            Log.d(TAG, "check reader: " + Thread.currentThread().toString());
                            if (image != null) {
                                emitter.onNext(image);
                                emitter.onComplete();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            emitter.onError(new Throwable("ImageReader error"));
                        }
                        mImageReader.setOnImageAvailableListener(null, null);
                    }

                };
                mImageReader.setOnImageAvailableListener(mImageListener, null);

            }
        }, BackpressureStrategy.DROP);
    }

    private Flowable<String> createFile() {
        return Flowable.create(new FlowableOnSubscribe<String>() {
            @Override
            public void subscribe(@NonNull FlowableEmitter<String> emitter) {
                Log.d(TAG, "check create filename: " + Thread.currentThread().toString());
                String directory, fileHead, fileName;
                int count = 0;

                directory = getExternalFilesDir(null) + "/screenshots/";

                Log.d(TAG, directory);
                File storeDirectory = new File(directory);
                if (!storeDirectory.exists()) {
                    boolean success = storeDirectory.mkdirs();
                    if (!success) {
                        emitter.onError(new Throwable("failed to create file storage directory."));
                        return;
                    }
                }

                fileHead = "temp";//Storage.getInstance(ScreenShotService.this).getUserId()+"@"+Storage.getInstance(ScreenShotService.this).getGuestId();
                fileName = directory + fileHead + count + ".jpg";
                File storeFile = new File(fileName);
                while (storeFile.exists()) {
                    count++;
                    fileName = directory + fileHead + count +".jpg";
                    storeFile = new File(fileName);
                }
                emitter.onNext(fileName);
                emitter.onComplete();
            }
        }, BackpressureStrategy.DROP).subscribeOn(Schedulers.io());
    }

    private void writeFile(Bitmap bitmap, String fileName) throws IOException {
        Log.d(TAG, "check write file: " + Thread.currentThread().toString());
        FileOutputStream fos = new FileOutputStream(fileName);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fos);
        fos.close();
        bitmap.recycle();
    }

    private Flowable<String> updateScan(final String fileName) {
        return Flowable.create(new FlowableOnSubscribe<String>() {
            @Override
            public void subscribe(@NonNull final FlowableEmitter<String> emitter) {
                String[] path = new String[]{fileName};
                mScanListener = new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String s, Uri uri) {
                        Log.d(TAG, "check scan file: " + Thread.currentThread().toString());
                        if (uri == null) {
                            emitter.onError(new Throwable("Scan fail" + s));
                        } else {
                            emitter.onNext(s);
                            emitter.onComplete();
                        }
                    }
                };
                MediaScannerConnection.scanFile(ScreenShotService.this, path, null, mScanListener);
            }
        }, BackpressureStrategy.DROP);
    }

    private void releaseScreenShot() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mImageReader != null) {
            mImageReader = null;
        }
        if (mImageListener != null) {
            mImageListener = null;
        }
        if (mScanListener != null) {
            mScanListener = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void deleteFolder() {
        String path = getExternalFilesDir(null) + "/screenshots/";
        File storeDirectory = new File(path);
        if (storeDirectory.isDirectory()) {
            String[] children = storeDirectory.list();
            if (children != null)
                for (String p : children) {
                    boolean b = new File(storeDirectory, p).delete();
                }
        }
    }
}