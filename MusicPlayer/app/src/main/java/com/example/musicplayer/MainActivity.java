package com.example.musicplayer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final int CODE_PERMISSION = 101;
    private static final int PHONE_CODE_PERMISSION = 102;
    private ListView mMusicListView;
    private ImageButton mLastMusicBtn;
    private ImageButton mPlayMusicBtn;
    private ImageButton mStopMusicBtn;
    private ImageButton mNextMusicBtn;
    private MusicAdapter mMusicAdapter;
    private ArrayList<Music> musicArrayList;
    private MediaPlayer mediaPlayer;
    private int number = 0;
    private int status = MusicService.STATUS_STOPPED;
    private TextView playTimeTv;
    private TextView totalTimeTv;
    private SeekBar processSeekbar;

    private long recordTime = 0; //当前播放进度
    private long totalTime = 0; //每首歌的总时间

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mediaPlayer = new MediaPlayer();
        initView();
        checkPermission();
        startService(new Intent(MainActivity.this,MusicService.class));
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
        registerReceiver(new StatusChangedBroadcastReceiver(),filter);
    }

    //运行时权限检查
    private void checkPermission(){
        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},CODE_PERMISSION);
        }else{
            initMusicList();
        }
        if(ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.READ_PHONE_STATE )!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_PHONE_STATE},PHONE_CODE_PERMISSION);
        }else{
            sendBroadcastOnCommand(MusicService.COMMAND_PHONE);
        }
    }
    private void  initView(){
        mMusicListView = (ListView)findViewById(R.id.musicListView);
        mLastMusicBtn = (ImageButton)findViewById(R.id.lastMusicBtn);
        mPlayMusicBtn = (ImageButton)findViewById(R.id.playMusicBtn);
        mStopMusicBtn = (ImageButton)findViewById(R.id.pauseMusicBtn);
        mNextMusicBtn = (ImageButton)findViewById(R.id.nextMusicBtn);
        mLastMusicBtn.setOnClickListener(this);
        mPlayMusicBtn.setOnClickListener(this);
        mStopMusicBtn.setOnClickListener(this);
        mNextMusicBtn.setOnClickListener(this);
        musicArrayList = MusicList.getMusicArrayList();
        mMusicAdapter = new MusicAdapter(MainActivity.this);
        mMusicListView.setAdapter(mMusicAdapter);
        playTimeTv = (TextView)findViewById(R.id.playTimeTv);
        totalTimeTv = (TextView)findViewById(R.id.totalTimeTv);
        processSeekbar = (SeekBar)findViewById(R.id.processSeekbar);
        processSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                recordTime = seekBar.getProgress() * totalTime /100;
                sendBroadcastOnCommand(MusicService.COMMAND_SEEKTO);
            }
        });
    }

    //获取内部存储设备上的音乐文件
    private void initMusicList(){
        Log.i("wanlijun","就是歌多");
        if(musicArrayList.isEmpty()){
            Cursor mCursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,null,null,null,MediaStore.Audio.AudioColumns.TITLE);
            int nameIndex = mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE);
            int artistIndex = mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST);
            int pathIndex = mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA);
            int timeIndex = mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION);
            if(mCursor != null){
                Log.i("wanlijun",mCursor.getCount()+"");
                for (mCursor.moveToFirst();!mCursor.isAfterLast();mCursor.moveToNext()){
                    String name = mCursor.getString(nameIndex);
                    Log.i("wanlijun","一首歌="+name);
                    String artist = mCursor.getString(artistIndex);
                    String path = mCursor.getString(pathIndex);
                    String time = mCursor.getString(timeIndex);
                    Music music = new Music();
                    music.setName(name);
                    music.setArtist(artist);
                    music.setPath(path);
                    music.setTime(time);
                    musicArrayList.add(music);
                }
                mMusicAdapter.notifyDataSetChanged();
            }
        }
        mMusicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                number = i;
                sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
            }
        });
    }




    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CODE_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            initMusicList();
        }else{
            Toast.makeText(MainActivity.this,"读取存储卡权限被拒绝，无法获取本地音乐",Toast.LENGTH_LONG).show();
        }
        if(requestCode == PHONE_CODE_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            sendBroadcastOnCommand(MusicService.COMMAND_PHONE);
        }else{

        }
    }

    //用广播的形式给service发命令
    private void sendBroadcastOnCommand(int command){
        Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_CONTROL);
        intent.putExtra("command",command);
        switch (command){
            case MusicService.COMMAND_PLAY:
                intent.putExtra("number",number);
                break;
                case MusicService.COMMAND_LAST:
                    break;
                    case MusicService.COMMAND_NEXT:
                        break;
                        case MusicService.COMMAND_PAUSE:
                            break;
                            case MusicService.COMMAND_RESUME:
                                break;
                                case MusicService.COMMAND_STOP:
                                    break;
            case MusicService.COMMAND_SEEKTO:
                intent.putExtra("seekto",recordTime);
                break;
                                    default:
                                        break;
        }
        sendBroadcast(intent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.lastMusicBtn:
                sendBroadcastOnCommand(MusicService.COMMAND_LAST);
                break;
            case R.id.playMusicBtn:
                sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
                break;
            case R.id.pauseMusicBtn:
                sendBroadcastOnCommand(MusicService.COMMAND_STOP);
                break;
            case R.id.nextMusicBtn:
                sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
                break;
        }
    }

    /**
     * 在设置按返回键退出程序的过程中，我发现按返回键后MainActivity执行了onDestroy，但是最近任务列表中仍然有该程序的图标
     * 点击任务列表中的图标，会重新执行MainActivity的onCreate,但是进程号还是原来的进程号，也就是按返回键不会杀死进程，只是进入了后台
     * 清除任务列表的任务后，进程就会被杀死了
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i("wanlijun","onKeyDown");
//        if(keyCode == KeyEvent.KEYCODE_BACK){
//            Log.i("wanlijun","onKeyDown");
//            finish();
//            return  true;
//        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    protected void onDestroy() {
        Log.i("wanlijun", Process.myPid()+"");
        Log.i("wanlijun","onDestroy");
        super.onDestroy();
    }

    //毫秒转成00:00（分：秒）形式
     public String getFormatTime(long longTime){
         SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
         Date date = new Date(longTime);
         return  sdf.format(date);
     }


    class MusicAdapter extends BaseAdapter{
        private Context mContext;
        public MusicAdapter(Context context){
            this.mContext = context;
        }
        @Override
        public Object getItem(int i) {
            return musicArrayList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getCount() {
            return musicArrayList.size();
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder holder = null;
            if(view == null){
                view = LayoutInflater.from(mContext).inflate(R.layout.item_layout,null);
                holder = new ViewHolder(view);
            }else{
                holder = (ViewHolder) view.getTag();
            }
            if(holder != null){
                Music music = musicArrayList.get(i);
                holder.musicNameTv.setText(music.getName());
                holder.musicArtistTv.setText(music.getArtist());
            }
            return view;
        }
        class ViewHolder{
            TextView musicNameTv;
            TextView musicArtistTv;
            public ViewHolder(View view){
                musicNameTv = (TextView)view.findViewById(R.id.musicNameTv);
                musicArtistTv = (TextView)view.findViewById(R.id.musicArtistTv);
                view.setTag(this);
            }
        }
    }

    //接受service发出的广播，根据当前播放器的状态改变seekbar的状态
    class StatusChangedBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("wanlijun","action:"+action);
            if(action.equals(MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS)){
                switch (intent.getIntExtra("status",MusicService.STATUS_STOPPED)){
                    case MusicService.STATUS_PLAYING:
                        status = MusicService.STATUS_PLAYING;
                        long totalTime1 = intent.getLongExtra("totalTime",0);
                        totalTimeTv.setText(getFormatTime(totalTime1));
                        long recordTime1 = intent.getLongExtra("currentTime",0);
                        playTimeTv.setText(getFormatTime(recordTime1));
                        int progress = (int) (recordTime1 * 100 / totalTime1);
                        processSeekbar.setProgress(progress);
                        totalTime = totalTime1;
                        recordTime = recordTime1;
                        break;
                    case MusicService.STATUS_PAUSED:
                        status = MusicService.STATUS_PAUSED;
                        break;
                    case MusicService.STATUS_STOPPED:
                        status = MusicService.STATUS_STOPPED;
                        totalTimeTv.setText("00:00");
                        playTimeTv.setText("00:00");
                        processSeekbar.setProgress(0);
                        recordTime = 0;
                        break;
                    case MusicService.STATUS_COMPLETED:
                        status = MusicService.STATUS_COMPLETED;
                        break;
                    case MusicService.STATUS_SEEKTO:
                        //拉进度条跳到指定位置播放时已经有一个子线程在执行，无需开启另一个子线程
                        status = MusicService.STATUS_PLAYING;
//                        mHandler.postDelayed(runnable,1000);
                        break;
                    case MusicService.STATUS_RESUMED:
                        //暂停后又开始播放
                        status = MusicService.STATUS_PLAYING;
                        break;
                }
            }
        }
    }

}
