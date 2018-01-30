package com.example.musicplayer;

import java.util.ArrayList;

/**
 * @author wanlijun
 * @description 歌曲列表
 * @time 2018/1/26 18:23
 */

public class MusicList {
    private static ArrayList<Music> musicArrayList = new ArrayList<>();
    private  MusicList(){

    }
    public static ArrayList<Music> getMusicArrayList(){
        return  musicArrayList;
    }
}
