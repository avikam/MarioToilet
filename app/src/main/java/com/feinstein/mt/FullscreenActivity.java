package com.feinstein.mt;

import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.instacart.library.truetime.TrueTime;

import java.io.IOException;
import java.util.Date;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private MediaPlayer mediaPlayer;

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;
    private boolean timeSynced = false;
    private boolean surfaceCreated = false;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private SurfaceView surfaceView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            surfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private final Runnable syncTime = new Runnable() {
        @Override
        public void run() {
            try {
                // TODO: In Loop
                TrueTime.build().initialize();
                timeSynced = true;

                Date noReallyThisIsTheTrueDateAndTime = TrueTime.now();
                noReallyThisIsTheTrueDateAndTime.getTime();
                Log.v("TIME_DEBUG", noReallyThisIsTheTrueDateAndTime.toString());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private final Runnable videoManager = new Runnable() {
        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        public void run() {
            while (!(timeSynced && surfaceCreated)) {
                Log.v("videoManager", "waiting for init");
                SystemClock.sleep(1000);
            }
            Log.v("videoManager", "ready for business");
            int videoDuration = mediaPlayer.getDuration();
            Log.v("videoManager", String.format("Video Duration = %d", videoDuration));
            int syncDuration = videoDuration + 1000*5;

            while (true) {
              try {
                TrueTime.build().initialize();
              } catch (IOException e) {
                e.printStackTrace();
              }
              Date myDate = TrueTime.now();
              long currentTime = myDate.getTime();
              Log.v("videoManager", String.format("Loop Start Time = %d", currentTime));
              long currentOffset = currentTime % syncDuration;
              Log.v("videoManager", String.format("currentOffset = %d", currentOffset));
              long nextRun = currentTime + (syncDuration - currentOffset);
              long timeTillNextRun = nextRun - currentTime;
              Log.v("videoManager", String.format("Next Run = %d, sleep for %d", nextRun,
                      timeTillNextRun));
              while (currentTime < nextRun){
                currentTime = TrueTime.now().getTime();
                timeTillNextRun = nextRun - currentTime;
                long sleepTime = timeTillNextRun / 2;
                Log.v("videoManager", String.format("currentTime = %d, sleepTime = %d",
                        currentTime,
                        sleepTime));
                SystemClock.sleep(timeTillNextRun / 2);
              }
              currentTime = TrueTime.now().getTime();
              Log.v("videoManager", String.format("Video Start Time = %d", currentTime));
              mediaPlayer.start();
            }
        }
    };

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);


        // Set up the user interaction to manually show or hide the system UI.
//    mContentView.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View view) {
//        toggle();
//      }
//    });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);


        mediaPlayer = MediaPlayer.create(this, R.raw.vidcap0);
        mediaPlayer.setLooping(false);
        surfaceView = findViewById(R.id.marioVideoSurface);
        surfaceView.getHolder().addCallback(this);

        Thread thread = new Thread(syncTime);
        thread.start();
        Thread thread2 = new Thread(videoManager);
        thread2.start();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        surfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mediaPlayer.setDisplay(surfaceHolder);
        surfaceCreated = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}
