package com.gluonhq.helloandroid;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import java.util.concurrent.*;

public class MainActivity extends Activity implements SurfaceHolder.Callback,
        SurfaceHolder.Callback2 {

    private static MainActivity   instance;
    private static FrameLayout  mViewGroup;
    private static SurfaceView  mView;
    private long nativeWindowPtr;
    private static final String TAG     = "GraalActivity";


   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
Log.v(TAG, "oncreate start");
System.err.println("Hello WORLD ACTIVITY, onCreate called");
            super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        getWindow().setFormat(PixelFormat.RGBA_8888);


        mView = new InternalSurfaceView(this);
        mView.getHolder().addCallback(this);
        mViewGroup = new FrameLayout(this);
        mViewGroup.addView(mView);
        setContentView(mViewGroup);
        instance = this;
Log.v(TAG, "oncreate done");
   }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
Log.v(TAG, "surfacecreated start");
        Log.v(TAG, "[MainGraalActivity] Surface created in activity.");
        Log.v(TAG, "loading Graallib");
        System.loadLibrary("mygraal");
        Log.v(TAG, "loaded Graallib");
        nativeSetSurface(holder.getSurface());
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float density = metrics.density;
        Log.v(TAG, "metrics = "+metrics+", density = "+density);
        nativeWindowPtr = surfaceReady(holder.getSurface(), density);
        Log.v(TAG, "Surface created, native code informed about it, nativeWindow at "+nativeWindowPtr);
        Log.v(TAG, "We will now launch Graal in a separate thread");
try {
        final CountDownLatch cl = new CountDownLatch(1);
        Thread t = new Thread() {
            @Override public void run() {
                try {
Log.v(TAG, "really now START GRAALAPP");
                      startGraalApp();
Log.v(TAG, "donereally now STARTED GRAALAPP, wait 3 s");
 Thread.sleep(3000);
Log.v(TAG, "really STARTED GRAALAPP, waited 3s");
startNativeRendering(0);
Log.v(TAG, "started native rendering");
cl.countDown();
                } catch (Throwable t) {
Log.v(TAG, "ERROR STARTING GRAAL!");
System.err.println("ERROR STARTING GRAAL!");
                    t.printStackTrace();
                }
            }
        };
        t.start();
Log.v(TAG, "GRAAL THREAD STARTED now");
// cl.await(10, TimeUnit.SECONDS);
 } catch (Throwable t) {
 t.printStackTrace();
 }
Log.v(TAG, "GRAAL SURFACE created");
Log.v(TAG, "surfacecreated done");
    }

    private native void startNativeRendering(long dpy);

    private native void startGraalApp();
    // private native void testGL();
    private native long surfaceReady(Surface surface, float density);
    private native void nativeSetSurface(Surface surface);



    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                    int height) {
    Log.v(TAG, "[MainActivity] surfaceChanged, format = "+format+", width = "+width+", height = "+height);
}
    public void nosurfaceChanged(SurfaceHolder holder, int format, int width,
                    int height) {
Log.v(TAG, "surfacechanged start");
    Log.v(TAG, "[MainActivity] surfaceChanged, format = "+format+", width = "+width+", height = "+height);
        nativeSetSurface(holder.getSurface());
DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float density = metrics.density;
System.err.println("surfaceChanged, metrics = "+metrics+", density = "+density);
            // _surfaceChanged(holder.getSurface(), format, width, height);
Log.v(TAG, "surfacechanged done");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
System.err.println("[MainGraalActivity] surfaceDestroyed");
            // _surfaceChanged(null);
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
Log.v(TAG, "surfaceredraw needed start");
}
    public void nosurfaceRedrawNeeded(SurfaceHolder holder) {
        System.err.println("[MainGraalActivity] surfaceRedrawNeeded. For now, we ignore this");
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float density = metrics.density;
Log.v(TAG, "surfaceredraw needed done");
    }

    class InternalSurfaceView extends SurfaceView {
       public InternalSurfaceView(Context context) {
            super(context);
            setFocusableInTouchMode(true);
        }


    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy");
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onRestart() {
        Log.v(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        super.onStop();
    }


}
