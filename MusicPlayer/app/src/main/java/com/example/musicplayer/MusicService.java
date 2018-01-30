package com.example.musicplayer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.TimeUtils;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.concurrent.TimeUnit;

/**
 * @author wanlijun
 * @description
 * @time 2018/1/29 15:46
 */

public class MusicService extends Service {
    private static final int PHONE_CODE_PERMISSION = 102;
    //命令
    public static final int COMMAND_UNKNOWN = -1;
    public static final int COMMAND_PLAY = 0;
    public static final int COMMAND_PAUSE = 1;
    public static final int COMMAND_STOP = 2;
    public static final int COMMAND_RESUME = 3;
    public static final int COMMAND_LAST = 4;
    public static final int COMMAND_NEXT = 5;
    public static final int COMMAND_CHECH_IS_PLAYING = 6;
    public static final int COMMAND_SEEKTO = 7;
    public static final int COMMAND_PHONE = 8;
    //状态
    public static final  int STATUS_PLAYING = 0;
    public static final int STATUS_PAUSED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_SEEKTO = 4;
    public static final int STATUS_RESUMED = 5;
    //广播
    public static final String BROADCAST_MUSICSERVICE_CONTROL = "MusicService.action.control";
    public static final String BROADCAST_MUSICSERVICE_UPDATE_STATUS = "MusicService.action.update";
    public static final String BROADCAST_MUSICSERVICE_PLAY = "MusicService.action.play";
    public static final String BROADCAST_MUSICSERVICE_LAST = "MusicService.action.last";
    public static final String BROADCAST_MUSICSERVICE_NEXT= "MusicService.action.next";

    private MediaPlayer mediaPlayer;
    private int number = 0;
    private boolean isPause = false;
    private int status = MusicService.STATUS_STOPPED;
    private long recordTime = 0; //当前播放进度
    private long totalTime = 0; //每首歌的总时间
    private Handler mHandler = new Handler();
    private boolean phone = false;
    //只有在播放歌曲的时候才要通知activity更新seekbar的进度，其他时间为防止异常，让runnable一直运行
    //把handler和runnable放在service里面是为了按手机返回键退出程序后再进入程序时，seekbar的进度有问题，放在service里面后，按返回键service不是destroy，可以实时通知activity更新seekbar进度
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
           if(status == MusicService.STATUS_PLAYING && recordTime < totalTime) {
                recordTime += 1000;
                sendBroadcastOnStatusChanged(status);
                mHandler.postDelayed(this, 1000);
            }else{
               mHandler.postDelayed(this, 1000);
           }
        }
    };
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        //自动播放下一首
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                next();
            }
        });
        //拉动seekbar时
        mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
                isPause = false;
                status = MusicService.STATUS_PLAYING;
            }
        });
        //注册广播接收器用来接收activity的命令
        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_MUSICSERVICE_CONTROL);
        filter.addAction(BROADCAST_MUSICSERVICE_PLAY);
        registerReceiver(new ReceiveCommandBroadCastReceiver(),filter);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if(mediaPlayer != null){
            mediaPlayer.release();
        }
        super.onDestroy();
    }

    //播放状态改变的时候刷新界面
    private  void sendBroadcastOnStatusChanged(int status){
        Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
        intent.putExtra("status",status);
        switch (status){
            case STATUS_PLAYING:
                //传当前歌曲的总时间和播放时间给activity用于更新seekbar进度
                intent.putExtra("totalTime",totalTime);
                intent.putExtra("currentTime",recordTime);
                break;
                default:
                    break;
        }
        sendBroadcast(intent);
    }

    //歌曲加载
    private void load(int number){
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(MusicList.getMusicArrayList().get(number).getPath());
            mediaPlayer.prepare();
            //加载一首新的歌曲时初始化数据
            totalTime = Long.valueOf(MusicList.getMusicArrayList().get(number).getTime());
            recordTime = 0;
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    //歌曲停止
    private void stop(){
        mediaPlayer.stop();
        status = MusicService.STATUS_STOPPED;
        sendBroadcastOnStatusChanged(status);
    }
    //上一曲
    private void last(){
        if(number == 0){
            number = MusicList.getMusicArrayList().size()-1;
        }else{
            number--;
        }
        load(number);
        mediaPlayer.start();
        sendBroadcastOnStatusChanged(status);
    }
    //下一曲
    private void next(){
        if(number == MusicList.getMusicArrayList().size()-1){
            number = 0;
        }else{
            number++;
        }
        load(number);
        mediaPlayer.start();
        sendBroadcastOnStatusChanged(status);
    }
    //歌曲播放
    private void play(int select){
        Log.i("wanlijun",select+"");
        Log.i("wanlijun",number+"");

        showNotification();
        if(mediaPlayer != null && mediaPlayer.isPlaying() && number == select){
            //当前播放的歌曲和选中的歌曲是同一个的时候，暂停
            mediaPlayer.pause();
            isPause = true;
            status = MusicService.STATUS_PAUSED;
            sendBroadcastOnStatusChanged(status);
        }else{
            //当前播放的歌曲与选择的歌曲不是同一个，播放选择的歌曲
            number = select;
            if(!isPause){
                load(select);
                mediaPlayer.start();
                status = MusicService.STATUS_PLAYING;
                sendBroadcastOnStatusChanged(status);
                mHandler.postDelayed(runnable, 1000);
            }else{
                mediaPlayer.start();
//                status = MusicService.STATUS_RESUMED;
//                sendBroadcastOnStatusChanged(status);
                status = MusicService.STATUS_PLAYING;
            }

        }
    }

    //手动拉进度条，歌曲跳到指定的位置播放
    private void seekto(int time){
        mediaPlayer.seekTo(time);
    }


    /**
     * 状态栏通知，自定义布局，并且给控件绑定点击事件
     */
    private void showNotification(){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getBaseContext(),"");
        builder.setSmallIcon(R.drawable.junjie4);
        builder.setContentTitle("樱花树下的约定");
        RemoteViews remoteViews = new RemoteViews(getPackageName(),R.layout.music_notification);
        //哈哈，又学到一个知识，以下是给RemoteViews里面的控件绑定点击事件
        //当接受到BROADCAST_MUSICSERVICE_PLAY的广播后就开始处理响应控件的点击事件
        Intent intentPlay = new Intent();
        intentPlay.setAction(MusicService.BROADCAST_MUSICSERVICE_PLAY);
        PendingIntent pendingIntentPlay = PendingIntent.getBroadcast(getBaseContext(),0,intentPlay,0);
        remoteViews.setOnClickPendingIntent(R.id.playBtn,pendingIntentPlay);

        Intent intentLast = new Intent();
        intentLast.setAction(MusicService.BROADCAST_MUSICSERVICE_LAST);
        PendingIntent pendingIntentLast = PendingIntent.getBroadcast(getBaseContext(),0,intentLast,0);
        remoteViews.setOnClickPendingIntent(R.id.lastBtn,pendingIntentLast);

        Intent intentNext = new Intent();
        intentNext.setAction(MusicService.BROADCAST_MUSICSERVICE_NEXT);
        PendingIntent pendingIntentNext = PendingIntent.getBroadcast(getBaseContext(),0,intentNext,0);
        remoteViews.setOnClickPendingIntent(R.id.nextBtn,pendingIntentNext);

        builder.setContent(remoteViews);
        builder.setAutoCancel(false); //设置这个并没有什么卵用，因为通知右滑消失了
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        builder.setSound(uri);
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(),0,intent,PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        Notification notification = builder.build();
        notification.flags = Notification.FLAG_NO_CLEAR; //点击通知和清除通知的时候通知不消失，向右滑动通知时通知也不消失
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        notificationManager.notify(1,notification);
    }

     class ReceiveCommandBroadCastReceiver extends BroadcastReceiver{
         @Override
         public void onReceive(Context context, Intent intent) {
             String action = intent.getAction();
             Log.i("wanlijun","action:"+action);
             if(action.equals(BROADCAST_MUSICSERVICE_CONTROL)){
                 int command = intent.getIntExtra("command",COMMAND_UNKNOWN);
                 switch (command){
                     case COMMAND_PLAY:
                         int select = intent.getIntExtra("number",0);
                         play(select);
                         break;
                     case COMMAND_LAST:
                         last();
                         break;
                     case COMMAND_NEXT:
                         next();
                         break;
                     case COMMAND_STOP:
                         stop();
                         break;
                     case COMMAND_SEEKTO:
                         recordTime = intent.getLongExtra("seekto",0);
                         seekto((int) recordTime);
                         break;
                     case COMMAND_PHONE:
                         TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                         telephonyManager.listen(new MyPhoneListener(),PhoneStateListener.LISTEN_CALL_STATE);
                         break;
                 }
             }else if(action.equals(BROADCAST_MUSICSERVICE_PLAY)){
                 play(number);
             }else if(action.equals(BROADCAST_MUSICSERVICE_LAST)){
                 last();
             }else if(action.equals(BROADCAST_MUSICSERVICE_NEXT)){
                 next();
             }
         }
     }

    //监听来电,来电的时候暂停音乐
    public class MyPhoneListener extends  PhoneStateListener{
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state){
                case TelephonyManager.CALL_STATE_RINGING:
                    if(status == MusicService.STATUS_PLAYING){
                        mediaPlayer.pause();
                        isPause = true;
                        status = MusicService.STATUS_PAUSED;
                        sendBroadcastOnStatusChanged(status);
                        phone = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if(phone){
                        mediaPlayer.start();
                        isPause = false;
                        status = MusicService.STATUS_PLAYING;
//                        sendBroadcastOnStatusChanged(status);
                        phone = false;
                    }
                    break;
            }
        }
    }
}
