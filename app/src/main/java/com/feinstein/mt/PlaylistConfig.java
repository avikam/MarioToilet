package com.feinstein.mt;

import java.util.Arrays;
import java.util.List;

class PlaylistConfig {
  List<String> videos;


  static PlaylistConfig fromString(String str) {
    return new PlaylistConfig() {{
      videos = Arrays.asList(str.split("\n"));
    }};
  }
}
