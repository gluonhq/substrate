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

public class MainActivity extends Activity implements SurfaceHolder.Callback,
        SurfaceHolder.Callback2 {

    private static MainActivity   instance;
    private static FrameLayout  mViewGroup;
    private static SurfaceView  mView;
    private long nativeWindowPtr;
    private float density;

    private static final String TAG     = "GraalActivity";

    boolean graalStarted = false;


   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate start, using Android Logging v1");
        System.err.println("onCreate called, writing this to System.err");
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
        Log.v(TAG, "onCreate done");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated for "+this);
        Log.v(TAG, "loading Graallib");
        System.loadLibrary("mygraal");
        Log.v(TAG, "loaded Graallib");
        nativeSetDataDir(getApplicationInfo().dataDir);
        nativeSetSurface(holder.getSurface());
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        density = metrics.density;
        Log.v(TAG, "metrics = "+metrics+", density = "+density);
        nativeWindowPtr = surfaceReady(holder.getSurface(), density);
        Log.v(TAG, "Surface created, native code informed about it, nativeWindow at "+nativeWindowPtr);
        if (graalStarted) {
            Log.v(TAG, "GraalApp is already started.");
        } else {
            Log.v(TAG, "We will now launch Graal in a separate thread");
            Thread t = new Thread() {
                @Override public void run() {
                    startGraalApp();
                }
            };
            t.start();
            graalStarted = true;
            Log.v(TAG, "graalStarted true");
        }
        Log.v(TAG, "surfaceCreated done");
    }



    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                    int height) {
        Log.v(TAG, "surfaceChanged start");
        Log.v(TAG, "[MainActivity] surfaceChanged, format = "+format+", width = "+width+", height = "+height);
        nativeSetSurface(holder.getSurface());
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        density = metrics.density;
        Log.v(TAG, "surfaceChanged done, metrics = "+metrics+", density = "+density);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        System.err.println("[MainGraalActivity] surfaceDestroyed, ignore for now");
            // _surfaceChanged(null);
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        Log.v(TAG, "SurfaceRedraw needed start");
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Log.v(TAG, "ask native graallayer to redraw surface");
        nativeSurfaceRedrawNeeded();
        try {
            Thread.sleep(500);
            Log.v(TAG, "surfaceredraw needed part 1 done");
            nativeSurfaceRedrawNeeded();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.v(TAG, "surfaceredraw needed (and wait) done");
    }

    private native void startGraalApp();
    private native long surfaceReady(Surface surface, float density);
    private native void nativeSetSurface(Surface surface);
    private native void nativeSetDataDir(String datadir);
    private native void nativeSurfaceRedrawNeeded();
    private native void nativeGotTouchEvent(int pcount, int[] actions, int[] ids, int[] touchXs, int[] touchYs);
    private native void nativeGotKeyEvent(int action, int keycode);

    class InternalSurfaceView extends SurfaceView {
       private static final int ACTION_POINTER_STILL = -1;

       public InternalSurfaceView(Context context) {
            super(context);
            setFocusableInTouchMode(true);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getAction();
            int actionCode = action & MotionEvent.ACTION_MASK;
            final int pcount = event.getPointerCount();
            final int[] actions = new int[pcount];
            final int[] ids = new int[pcount];
            final int[] touchXs = new int[pcount];
            final int[] touchYs = new int[pcount];
            Log.v(TAG, "Activity, get touch event, pcount = "+pcount);
            if (pcount > 1) {
                //multitouch
                if (actionCode == MotionEvent.ACTION_POINTER_DOWN
                        || actionCode == MotionEvent.ACTION_POINTER_UP) {

                    int pointerIndex = event.getActionIndex();
                    for (int i = 0; i < pcount; i++) {
                        actions[i] = pointerIndex == i ? actionCode : ACTION_POINTER_STILL;
                        ids[i] = event.getPointerId(i);
                        touchXs[i] = (int) (event.getX(i)/density);
                        touchYs[i] = (int) (event.getY(i)/density);
                    }
                } else if (actionCode == MotionEvent.ACTION_MOVE) {
                    for (int i = 0; i < pcount; i++) {
                        touchXs[i] = (int) (event.getX(i)/density);
                        touchYs[i] = (int) (event.getY(i)/density);
                        actions[i] = MotionEvent.ACTION_MOVE;
                        ids[i] = event.getPointerId(i);
                    }
                }
            } else {
                //single touch
                actions[0] = actionCode;
                ids[0] = event.getPointerId(0);
                touchXs[0] = (int) (event.getX()/density);
                touchYs[0] = (int) (event.getY()/density);
            }
            nativeGotTouchEvent(pcount, actions, ids, touchXs, touchYs);
            return true;
        }

        @Override
        public boolean dispatchKeyEvent(final KeyEvent event) {
            Log.v(TAG, "Activity, get key event, action = "+event.getAction());
            nativeGotKeyEvent(event.getAction(), event.getKeyCode());
            return true;
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
        Log.v(TAG, "onResume done");
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart");
        super.onStart();
        Log.v(TAG, "onStart done");
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
