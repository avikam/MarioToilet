package com.feinstein.mt;

import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.instacart.library.truetime.TrueTime;

import java.io.File;
import java.io.IOException;
import java.util.Date;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity implements SurfaceHolder.Callback {
  private MediaPlayer mediaPlayer;

  // Identifier specifying which video to play
  private Integer playerName = null;

  // HTTP request queue
  private RequestQueue httpRequestQueue;

  static final String VideoFileName = "MarioToiletFile.3gp";
  static final String NewVideoFileName = "MarioToiletFile-New.3gp";

  /**
   * Whether or not the system UI should be auto-hidden after
   * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
   */
  private static final boolean AUTO_HIDE = true;

  private boolean timeSynced = false;
  private boolean surfaceCreated = false;
  private boolean videoAvailable = false;
  private boolean exited;

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
  private final Runnable mHideRunnable = () -> hide();

  private final Runnable syncRunnable = () -> {
    while (!exited) {
      try {
        // Resync Time
        TrueTime.build().initialize();
        timeSynced = true;

        // Poll HTTP Playlist, check for changes - Must have playerName to know how to deal with
        // the response
        if (playerName != null) {

          StringRequest stringRequest = new StringRequest(Request.Method.GET,
              "https://mario-toilet.s3-us-west-2.amazonaws.com/manifest.txt",
              (response) -> {
                Log.v("PLAYLIST", response);

                PlaylistConfig plConfig = PlaylistConfig.fromString(response);
                String videoUrl = plConfig.videos.get(playerName);
                Log.v("PLAYLIST", String.format("My Video: %s", videoUrl));

                SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
                String currentFileName = Prefrences.GetVideoFilename(sharedPreferences);

                Log.v("PLAYLIST", String.format("%s %s", currentFileName, videoUrl));
                if (currentFileName == null || !currentFileName.equals(videoUrl)) {
                  Log.v("PLAYLIST", "Downloading new file");
                  Prefrences.SetVideoFilename(sharedPreferences, videoUrl);
                  downloadVideoFile(videoUrl);
                } else {
                  videoAvailable = true;
                }

              },
              (error) -> {
                Log.e("PLAYLIST", error.toString());
              }
          );

          httpRequestQueue.add(stringRequest);
        }

        SystemClock.sleep(60000);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  };

  private final Runnable videoManager = new Runnable() {
    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {
      while (!(timeSynced && surfaceCreated && videoAvailable && (playerName != null))) {
        Log.v("videoManager", String.format(
            "waiting for init (%b %b %s)", timeSynced, surfaceCreated, playerName
        ));
        SystemClock.sleep(1000);
      }
      Log.v("videoManager",
          String.format("ready for business: playerName = %s", playerName));
      int videoDuration = mediaPlayer.getDuration();
      Log.v("videoManager", String.format("Video Duration = %d", videoDuration));
      int syncDuration = videoDuration + 1000 * 5;
      Log.v("videoManager", String.format("ready for business: videoName = %s", playerName));

      while (true) {
        try {
          TrueTime.build().initialize();
        } catch (IOException e) {
          e.printStackTrace();
        }
        Log.v("videoManager", String.format("Video Duration = %d", videoDuration));
        Date myDate = TrueTime.now();
        long currentTime = myDate.getTime();
        Log.v("videoManager", String.format("Loop Start Time = %d", currentTime));
        long currentOffset = currentTime % syncDuration;
        Log.v("videoManager", String.format("currentOffset = %d", currentOffset));
        long nextRun = currentTime + (syncDuration - currentOffset);
        long timeTillNextRun = nextRun - currentTime;
        Log.v("videoManager", String.format("Next Run = %d, sleep for %d", nextRun,
            timeTillNextRun));
        while (currentTime < nextRun) {
          currentTime = TrueTime.now().getTime();
          timeTillNextRun = nextRun - currentTime;
          long sleepTime = timeTillNextRun / 2;
          if (sleepTime < 0) sleepTime = 0;
          Log.v("videoManager", String.format("currentTime = %d, sleepTime = %d",
              currentTime,
              sleepTime));
          SystemClock.sleep(sleepTime);
        }
        currentTime = TrueTime.now().getTime();
        Log.v("videoManager", String.format("Video Start Time = %d", currentTime));
        mediaPlayer.start();
      }
    }
  };

  private long downloadID;


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

    SetPlayerName();
    setContentView(R.layout.activity_fullscreen);

    // Setup Media Player
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setLooping(true);
    surfaceView = findViewById(R.id.marioVideoSurface);
    surfaceView.getHolder().addCallback(this);

    mVisible = true;
    mControlsView = findViewById(R.id.fullscreen_content_controls);

    registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

    // Upon interacting with UI controls, delay any scheduled hide()
    // operations to prevent the jarring behavior of controls going away
    // while interacting with the UI.
    // findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

    httpRequestQueue = Volley.newRequestQueue(this);

    // Start threads
    exited = false;
    new Thread(syncRunnable).start();
    new Thread(videoManager).start();
  }

  private void SetPlayerName() {
    SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
    playerName = Prefrences.GetPlayerName(sharedPref);
    if (playerName == null) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle("Enter video name");

      // Set up the input
      final EditText input = new EditText(this);

      // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
      input.setInputType(InputType.TYPE_CLASS_TEXT);
      builder.setView(input);

      // Set up the buttons
      builder.setPositiveButton(
          "OK",
          (dialog, which) -> playerName = Prefrences.SetPlayerName(
              sharedPref, input.getText().toString())
      );

      builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
      builder.show();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    exited = true;
  }

  // Video file download management
  private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
      if (downloadID != id) {
        Log.w("DOWNLOAD", "Unknown file downloaded");
        return;
      }

      try {
        // Move new file to our dest file
        File newFile = getVideoFile(NewVideoFileName);
        newFile.renameTo(getVideoFile(VideoFileName));
        videoAvailable = true;

        // Change data source to the newly downloaded file
        mediaPlayer.reset();
        mediaPlayer.setDataSource(getVideoFile(VideoFileName).getPath());
        mediaPlayer.prepare();

      } catch (IOException e) {
        Log.e("DOWNLOAD", "Error while loading downloaded file");
        e.printStackTrace();
      }
    }
  };

  private void downloadVideoFile(String url) {
    File file = getVideoFile(NewVideoFileName);
    Log.v("DOWNLOAD", "Downloading to " + file.getPath());

    DownloadManager.Request request = new DownloadManager.Request(
        Uri.parse(url))
        .setTitle("Mario Toilet")// Title of the Download Notification
        .setDescription("Downloading Mario Toilet new video")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setDestinationUri(Uri.fromFile(file));

    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    downloadID = downloadManager.enqueue(request);
  }

  // End video file download management


  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);

    // Trigger the initial hide() shortly after the activity has been
    // created, to briefly hint to the user that UI controls
    // are available.
    delayedHide(100);
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

  private File getVideoFile(String filename) {
    return new File(getExternalFilesDir(null), filename);
  }
}
