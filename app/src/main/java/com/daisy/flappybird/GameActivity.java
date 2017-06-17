package com.daisy.flappybird;

import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class GameActivity extends AppCompatActivity {
    private GameView gameView;
    private TextView textViewScore;

    private MediaPlayer mediaPlayer;

    private int gameMode;

    private AudioRecorder audioRecorder;

    private static final int TOUCH_MODE = 0x00;
    private static final int VOICE_MODE = 0x01;

    private Timer timer;

    private int score = 0;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case UPDATE:
                    if (gameView.isAlive()) {
                        gameView.update();
                    } else {
                        // Derek is debugging...
                        Log.i("DerekDick", "MainActivity game over");

                        if (gameMode == TOUCH_MODE) {
                            // Cancel the timer
                            timer.cancel();
//                        timer.purge();
                        } else {
                            audioRecorder = null;
                        }

                        // Derek is debugging...
                        Log.i("DerekDick", "MainActivity timer cancelled");

                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(GameActivity.this);
                        alertDialog.setTitle("GAME OVER");
                        alertDialog.setMessage("Score: " + String.valueOf(gameView.getScore()) +
                                "\n" + "Would you like to RESTART?");
                        alertDialog.setCancelable(false);
                        alertDialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                GameActivity.this.restartGame();
                            }
                        });
                        alertDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                GameActivity.this.onBackPressed();
                            }
                        });
                        alertDialog.show();
                    }

                    break;

                case RESET_SCORE:
                    textViewScore.setText("0");

                    break;

                default:
                    break;
            }
        }
    };

    // The what values of the messages
    private static final int UPDATE = 0x00;
    private static final int RESET_SCORE = 0x01;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the status bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_touch);

        // Initialize the private views
        initViews();

        // Initialize the MediaPlayer
        mediaPlayer = MediaPlayer.create(this, R.raw.sound_score);
        mediaPlayer.setLooping(false);

        // Get the mode of the game from the StartingActivity
        if (getIntent().getStringExtra("Mode").equals("Touch")) {
            gameMode = TOUCH_MODE;
        } else {
            gameMode = VOICE_MODE;
        }

        // Set the Timer
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Sleep for 3 seconds for the Surface to initialize
                    Thread.sleep(3000);
                } catch (Exception exception) {
                    exception.printStackTrace();
                } finally {
                    setNewTimer();
                }
            }
        }).start();

        if (gameMode == TOUCH_MODE) {
            // Jump listener
            gameView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            gameView.jump();

                            break;

                        case MotionEvent.ACTION_UP:


                            break;

                        default:
                            break;
                    }

                    return true;
                }
            });
        } else {
            audioRecorder = new AudioRecorder();
            audioRecorder.getNoiseLevel();
        }
    }

    private class AudioRecorder {
        private static final String TAG = "AudioRecord";
        int SAMPLE_RATE_IN_HZ = 8000;
        int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
                AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord mAudioRecord;
        boolean isGetVoiceRun;
        Object mLock;

        public AudioRecorder() {
            mLock = new Object();
        }

        public void getNoiseLevel() {
            if (isGetVoiceRun) {
                Log.e(TAG, "还在录着呢");
                return;
            }
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
            if (mAudioRecord == null) {
                Log.e(TAG, "mAudioRecord初始化失败");
            }
            isGetVoiceRun = true;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    mAudioRecord.startRecording();
                    short[] buffer = new short[BUFFER_SIZE];
                    while (isGetVoiceRun) {
                        //r是实际读取的数据长度，一般而言r会小于buffersize
                        int r = mAudioRecord.read(buffer, 0, BUFFER_SIZE);
                        long v = 0;
                        // 将 buffer 内容取出，进行平方和运算
                        for (int i = 0; i < buffer.length; i++) {
                            v += buffer[i] * buffer[i];
                        }
                        // 平方和除以数据总长度，得到音量大小。
                        double mean = v / (double) r;
                        double volume = 10 * Math.log10(mean);
//                        Log.i(TAG, "分贝值:" + volume);

                        // Jump if the volume is loud enough
                        if (volume > 60.0) {
                            GameActivity.this.gameView.jump();
                            Log.i(TAG, "分贝值: " + volume + "超过了");
                        }

                        // 大概一秒十次
                        synchronized (mLock) {
                            try {
                                mLock.wait(70);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mAudioRecord.stop();
                    mAudioRecord.release();
                    mAudioRecord = null;
                }
            }).start();
        }
    }

    private void initViews() {
        /* Initializes the private views */

        gameView = (GameView) findViewById(R.id.game_view);
        textViewScore = (TextView) findViewById(R.id.text_view_score);
    }

    private void setNewTimer() {
        /* Sets the Timer to update the UI of the GameView  */

        // Derek is debugging...
        Log.i("DerekDick", "MainActivity setNewTimer");

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Send the message to the handler to update the UI of the GameView
                GameActivity.this.handler.sendEmptyMessage(UPDATE);

                // For garbage collection
                System.gc();
            }
        }, 0, 17);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        timer.cancel();
        timer.purge();
    }

    @Override
    protected void onPause() {
        super.onPause();

//        timer.cancel();
//        timer.purge();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

//        setNewTimer();
    }

    public void updateScore(int score) {
        /* Updates the displayed score */

        textViewScore.setText(String.valueOf(score));
    }

    public void playScoreMusic() {
        /* Plays the music for score */

        // Derek is debugging...
        Log.i("DerekDick", "MainActivity playScoreMusic");

        mediaPlayer.start();
    }

    private void restartGame() {
        /* Restarts the game */

        // Reset all the data of the over game in the GameView
        gameView.resetData();

        // Refresh the TextView for displaying the score
        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.sendEmptyMessage(RESET_SCORE);
            }
        }).start();

        if (gameMode == TOUCH_MODE) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Sleep for 3 seconds
                        Thread.sleep(3000);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    } finally {
                        setNewTimer();
                    }
                }
            }).start();
        } else {
            audioRecorder = new AudioRecorder();
            audioRecorder.getNoiseLevel();
        }
    }
}