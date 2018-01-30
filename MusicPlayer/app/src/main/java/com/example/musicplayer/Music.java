package com.example.musicplayer;

/**
 * @author wanlijun
 * @description  歌曲的基本属性
 * @time 2018/1/26 18:20
 */

public class Music {
    private String name;   //歌曲名
    private String artist;   //艺术家名
    private String path;   //歌曲路径
    private String time;   //歌曲总时长

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
