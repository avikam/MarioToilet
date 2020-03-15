package com.feinstein.mt;
import android.content.SharedPreferences;



public class Prefrences {
  private final static String PLAYER_NAME = "player_name";
  private final static String VIDEO_FILE_NAME = "video_file_name";

  public static Integer GetPlayerName(SharedPreferences sharedPref) {
    if (!sharedPref.contains(PLAYER_NAME)) {
      return null;
    } else {
      return sharedPref.getInt(PLAYER_NAME, -1);
    }
  }

  public static Integer SetPlayerName(SharedPreferences sharedPref, String input) {
    final SharedPreferences.Editor editor = sharedPref.edit();
    int playerName = Integer.parseInt(input);
    editor.putInt(PLAYER_NAME, playerName);
    editor.apply();
    return playerName;
  }


  public static String GetVideoFilename(SharedPreferences sharedPref) {
    if (!sharedPref.contains(VIDEO_FILE_NAME)) {
      return null;
    } else {
      return sharedPref.getString(VIDEO_FILE_NAME, null);
    }
  }

  public static String SetVideoFilename(SharedPreferences sharedPref, String videoName) {
    final SharedPreferences.Editor editor = sharedPref.edit();
    editor.putString(VIDEO_FILE_NAME, videoName);
    editor.apply();

    return videoName;
  }
}
