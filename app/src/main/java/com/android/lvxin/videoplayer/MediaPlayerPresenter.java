package com.android.lvxin.videoplayer;

import android.content.ContentUris;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.android.lvxin.data.AudioInfo;
import com.android.lvxin.data.VideoInfo;
import com.android.lvxin.data.source.MediasDataSource;
import com.android.lvxin.data.source.MediasRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName: MediaPlayerPresenter
 * @Description: TODO
 * @Author: lvxin
 * @Date: 6/14/16 15:22
 */
public class MediaPlayerPresenter implements MediaPlayerContract.Presenter {
    private static final String TAG = "MediaPlayerPresenter";
    private MediasRepository mRepository;
    private MediaPlayerContract.View mView;

    private List<VideoInfo> mVideoData = new ArrayList<>();
    private List<AudioInfo> mAudioData = new ArrayList<>();
    private int currentVideoIndex = 0;
    private int currentAudioIndex = 0;
    private int currentVideoRepeats = 0;

    private VideoPlayerPresenter mVideoPlayer;
    private MediaPlayer mAudioPlayer;
    private MediaPlayer mBGAudioPlayer;
    private Context mContext;
    private boolean isPlaying = true;
    private int videoPlayedPosition = 0; // 到后台是视频播放的位置
    private int audioPlayedPosition = 0; // 到后台是音频播放的位置
    private long totalPlayTime; // 播放总时长
    private long remainTime; // 剩余播放时间
    private int restTime; // 休息时长
    private MediaPlayerContract.MediaStatus status;

    private boolean isResting = false; // 是否处于休息状态

    private boolean isPrepareRest = false;
    private boolean isPrepareStart = true;
    private boolean isResumeStart = false;
    private boolean isResumePause = false;

    public MediaPlayerPresenter(MediasRepository repository, MediaPlayerContract.View view) {
        mRepository = repository;
        mView = view;
        view.setPresenter(this);
    }

    @Override
    public void start() {
        loadData();
    }

    @Override
    public void loadData() {
        // TODO 获取播放的总时长
        totalPlayTime = remainTime = 22;

        mRepository.getVideos(new MediasDataSource.LoadVideosCallback() {
            @Override
            public void onVideosLoaded(List<VideoInfo> videos) {
                if (videos.size() > 1) {
                    mVideoData = videos.subList(0, 2);
                } else {
                    mVideoData = videos;
                }
            }

            @Override
            public void onDataNotAvailable() {
                // TODO
            }
        });

        mRepository.getAudios(new MediasDataSource.LoadAudiosCallback() {
            @Override
            public void onAudiosLoaded(List<AudioInfo> audios) {
                if (audios.size() > 1) {
                    mAudioData = audios.subList(0, 2);
                } else {
                    mAudioData = audios;
                }
            }

            @Override
            public void onDataNotAvailable() {
                // TODO
            }
        });
    }

    @Override
    public List<VideoInfo> getVideos() {
        return mVideoData;
    }

    @Override
    public Uri getMediaUri(Uri contentUri, long id) {
        return ContentUris.withAppendedId(contentUri, id);
    }

    private Uri getAudioUri(long id) {
        return getMediaUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    @Override
    public VideoInfo getVideo(int index) {
        return mVideoData.get(index);
    }

    @Override
    public void setupMediaPlayer(final Context context, SurfaceHolder surfaceHolder) {
        this.mContext = context;

        VideoInfo video = mVideoData.get(currentVideoIndex);
        mView.setupProgressBar(mVideoData.size(), video.videoDuration * video.repeats);

        mView.setupMaxCircleProgress(mVideoData.size());

        setupAudioPlayer(context);
        setupVideoPlayer(context, surfaceHolder);
    }

    private void setupVideoPlayer(final Context context, SurfaceHolder surfaceHolder) {
        mVideoPlayer = new VideoPlayerPresenter().setup(surfaceHolder);
        String videoPath = getDataSourcePath(getVideo(currentVideoIndex).videoName);
        mVideoPlayer.prepareAsync(videoPath);

        mVideoPlayer.setOnPreparedListener(new VideoPlayerPresenter.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                onVideoPrepared(mp);
            }
        });

        mVideoPlayer.setOnCompletionListener(new VideoPlayerPresenter.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onVideoPlayCompleted(context, mp);
            }
        });
    }

    private void setupAudioPlayer(final Context context) {
        mAudioPlayer = new MediaPlayer();
        prepareAudio(context);

        // 背景音乐
//        mBGAudioPlayer = new MediaPlayer();
//        prepareAudio(context, mBGAudioPlayer, getAudioUri(Tools.generateAudioItems(context).get(1).audioId));
    }

    @Override
    public void prepareAudio(Context context) {
        try {
            mAudioPlayer.setDataSource(context, getAudioUri(mAudioData.get(currentAudioIndex).audioId));
            mAudioPlayer.prepareAsync();
            mAudioPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStartPlay() {
        mVideoPlayer.start();
        mView.startProgressBar();

        mView.updateCircleProgress(currentVideoIndex + 1);
        mView.updateRemainTimeText(remainTime);
    }

    @Override
    public void onVideoPrepared(MediaPlayer mp) {
        //                    mMediaPlayer.setVolume(0.5f, 0.5f);
        if (isPrepareStart) {
            onStartPlay();
            isPrepareStart = false;
        } else if (isResumeStart) {
            mVideoPlayer.seekToStart(videoPlayedPosition);
            isResumeStart = false;
        } else if (isResumePause) {
            mVideoPlayer.seekToPause(videoPlayedPosition);
            isResumePause = false;
        } else if (isPrepareRest) {
            mVideoPlayer.start();
            mVideoPlayer.pauseAndSeekTo(0);
        }
    }

    @Override
    public void onVideoPlayCompleted(Context context, MediaPlayer mp) {
        ++currentVideoRepeats;
        if (currentVideoRepeats >= mVideoData.get(currentVideoIndex).repeats) {

            // TODO 更新音频相关信息
            mAudioPlayer.reset();
            ++currentAudioIndex;

            // 更新视频相关信息
            currentVideoRepeats = 0;
            mVideoPlayer.reset();
            ++currentVideoIndex;
            if (mVideoData.size() > currentVideoIndex) {
                // 休息倒计时页面
                // 暂停进度条
                onRest(getVideo(currentVideoIndex - 1).restTime);
            } else {
                Toast.makeText(context, "视频播放完毕", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 重复播放当前多媒体文件
            mVideoPlayer.start();
            // TODO 是否需要更换不同的音频
        }
    }

    @Override
    public void onRest(int restTime) {
        isResting = true;
        this.restTime = restTime;
        //暂停剩余播放时间
        mView.pauseRemainTime();

        // 显示休息页面
        mView.showRestPage(true, restTime);

        // 更新底部精度条
        VideoInfo videoInfo = mVideoData.get(currentVideoIndex);
        mView.updateProgressBar(true, videoInfo.videoDuration * videoInfo.repeats);
        //准备下一个视频
        isPrepareRest = true;
        String dataSource = getDataSourcePath(videoInfo.videoName);
        mVideoPlayer.prepareAsync(dataSource);
    }

    @Override
    public void updateRestCountDown() {
        mView.updateCountdownView(--restTime);
    }

    @Override
    public void onSkipRest() {
        mView.stopCountdownProgress();
        startAfterRest();
    }

    @Override
    public void startAfterRest() {
        isResting = false;
        mView.showRestPage(false, 0);
        mView.updateCircleProgress(currentVideoIndex + 1);

        // 播放视频及相应的控件
        onStartPlay();

        // TODO 休息后播放视频对应的音频
//        prepareAudio(mContext);
    }

    @Override
    public void continuePlayMedia() {
        // 继续播放视频
        if (null != mVideoPlayer && !mVideoPlayer.isPlaying()) {
            mVideoPlayer.start();
        }

        // 继续播放音频
        if (null != mAudioPlayer && !mAudioPlayer.isPlaying()) {
            mAudioPlayer.start();
        }
        // 继续播放背景音乐
        if (null != mBGAudioPlayer) {
            mBGAudioPlayer.start();
        }

        mView.updateRemainTimeText(remainTime);
    }

    @Override
    public void pauseMedia() {
        mView.pauseRemainTime();
        // 暂停多媒体文件

        if (null != mVideoPlayer && mVideoPlayer.isPlaying()) {
            mVideoPlayer.pause();
            videoPlayedPosition = mVideoPlayer.getCurrentPosition();
        }
        if (null != mAudioPlayer && mAudioPlayer.isPlaying()) {
            mAudioPlayer.pause();
            audioPlayedPosition = mAudioPlayer.getCurrentPosition();
        }
        if (null != mBGAudioPlayer && mBGAudioPlayer.isPlaying()) {
            mBGAudioPlayer.pause();
        }
    }

    @Override
    public void resumeAndStartMedia() {
        isResumeStart = true;
    }


    @Override
    public void resumeAndPauseMedia() {
        isResumePause = true;
    }

    @Override
    public void setVideoPlayedPosition(int videoPlayedPosition) {
        this.videoPlayedPosition = videoPlayedPosition;
    }

    @Override
    public void setAudioPlayedPosition(int audioPlayedPosition) {
        this.audioPlayedPosition = audioPlayedPosition;
    }

    @Override
    public void nextMedia(Context context) {
        mView.pauseRemainTime();
        if (mVideoData.size() - 1 > currentVideoIndex) {
            ++currentVideoIndex;
            ++currentAudioIndex;
            startPreparedMedia(context, true);
        }
    }

    @Override
    public void prevMedia(Context context) {
        mView.pauseRemainTime();
        if (0 < currentVideoIndex) {
            --currentVideoIndex;
            --currentAudioIndex;
            startPreparedMedia(context, false);
        }
    }

    private long calculateRemainTime(int currentIndex) {
        long passedTime = 0;
        for (int i = 0; i < currentIndex; i++) {
            passedTime += getVideo(i).repeats * getVideo(i).videoDuration;
        }
        return totalPlayTime - passedTime;
    }

    private void startPreparedMedia(Context context, boolean isNext) {
        // 获取剩余播放时间
        remainTime = calculateRemainTime(currentVideoIndex);
        currentVideoRepeats = 0;
        // 0. 更新圆形进度条
        mView.updateCircleProgress(1 + currentVideoIndex);
        // 1. 更新进度条的进度
        VideoInfo videoInfo = mVideoData.get(currentVideoIndex);
        mView.updateProgressBar(isNext, videoInfo.repeats * videoInfo.videoDuration);
        // 2. 结束当前视频,开始下一个/上一个视频
//        mMediaPlayer.reset();
        mVideoPlayer.reset();
        isPrepareStart = true;
//        prepareAndStartVideo(context);
        String dataSource = getDataSourcePath(getVideo(currentVideoIndex).videoName);
        mVideoPlayer.prepareAsync(dataSource);
        // 3. 结束当前音频, 开始下一个/上一个音频
        mAudioPlayer.reset();
        prepareAudio(context);
    }

    @Override
    public boolean getPlayingStatus() {
        return isPlaying;
    }

    @Override
    public void updatePlayStatus() {
        if (isPlaying) {
            isPlaying = false;
            pauseMedia();
        } else {
            isPlaying = true;
            continuePlayMedia();
        }
        //更新ui
        mView.updatePlayPauseView(isPlaying);
    }

    @Override
    public void updateRemainTime() {
        mView.updateRemainTimeText(--remainTime);
    }

    @Override
    public boolean hasRemainTime() {
        return 0 < remainTime;
    }

    @Override
    public MediaPlayerContract.MediaStatus getMediaStatus() {
        return status;
    }

    @Override
    public void setMediaStatus() {
        if (isResting) {
            this.status = MediaPlayerContract.MediaStatus.IS_RESTING;
        } else {
            if (mVideoPlayer.isPlaying()) {
                this.status = MediaPlayerContract.MediaStatus.IS_PLAYING;
            } else {
                this.status = MediaPlayerContract.MediaStatus.PAUSE;
            }
        }
    }

    @Override
    public void onRelease() {
        mVideoPlayer.release();

        if (null != mAudioPlayer) {
            mAudioPlayer.release();
        }
        mAudioPlayer = null;

        if (null != mBGAudioPlayer) {
            mBGAudioPlayer.release();
        }
        mBGAudioPlayer = null;
    }

    private String getDataSourcePath(String fileName) {
        Log.i(TAG, "getDataSourcePath: " + mContext.getExternalFilesDir(null).getAbsolutePath());
        return mContext.getExternalFilesDir(null).getAbsolutePath() + "/" + fileName;
    }
}
