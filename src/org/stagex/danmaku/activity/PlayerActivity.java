package org.stagex.danmaku.activity;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.stagex.danmaku.R;
import org.stagex.danmaku.comment.CPI;
import org.stagex.danmaku.comment.CommentDrawable;
import org.stagex.danmaku.comment.CommentManager;
import org.stagex.danmaku.wrapper.VLC;
import org.stagex.danmaku.wrapper.VLI;
import org.stagex.danmaku.wrapper.VLM;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class PlayerActivity extends Activity implements VLI, CPI,
		OnTouchListener, OnClickListener, OnSeekBarChangeListener {

	private VLC mVLC = VLC.getInstance();
	private VLM mVLM = VLM.getInstance();

	private Handler mEventHandler;

	// player misc
	private ProgressBar mProgressBarPrepairing;

	private boolean mPrepairDone = false;

	private ReentrantLock mPrepairLock = new ReentrantLock();
	private Condition mPrepairCond = mPrepairLock.newCondition();
	private Thread mPrepairThread = null;

	// player controls
	private TextView mTextViewPosition;
	private SeekBar mSeekBarProgress;
	private TextView mTextViewLength;
	private ImageButton mImageButtonAudio;
	private ImageButton mImageButtonSubtitle;
	private ImageButton mImageButtonPrev;
	private ImageButton mImageButtonPlay;
	private ImageButton mImageButtonNext;
	private ImageButton mImageButtonChat;

	private LinearLayout mLinearLayoutControlBar;

	// player video
	private View mViewMessage;
	private SurfaceView mSurfaceViewVideo;
	private SurfaceHolder mSurfaceHolderVideo;

	private boolean mVideoSurfaceReady = false;

	private CommentManager mCommentManager = new CommentManager();

	//
	private ArrayList<String> mPlayList = null;
	private int mCurrentIndex = -1;

	private String mUriVideo;
	private String mUriMessage;

	//
	private int mCurrentState = -1;
	private int mCurrentTime = -1;
	private int mCurrentLength = -1;

	private int mCanPause = -1;
	private int mCanSeek = -1;

	private int mVideoOriginalWidth = -1;
	private int mVideoOriginalHeight = -1;
	private int mVideoSurfaceWidth = -1;
	private int mVideoSurfaceHeight = -1;

	protected void initializeEvents() {
		mEventHandler = new Handler() {
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case CPI.EVENT_COMMENT_SNAPSHOT: {
					Bitmap bitmap = (Bitmap) msg.obj;
					CommentDrawable snapshot = (CommentDrawable) mViewMessage
							.getBackground();
					snapshot.setSize(mViewMessage.getWidth(),
							mViewMessage.getHeight());
					snapshot.setBitmap(bitmap);
					mViewMessage.invalidate();
					break;
				}
				case VLI.EVENT_INPUT_STATE: {
					int state = msg.arg1;
					switch (state) {
					case VLI.INPUT_STATE_PLAY: {
						mCommentManager.play();
						mImageButtonPlay.setImageResource(R.drawable.pause);
						break;
					}
					case VLI.INPUT_STATE_PAUSE: {
						mCommentManager.pause();
						mImageButtonPlay.setImageResource(R.drawable.play);
						break;
					}
					case VLI.INPUT_STATE_END: {
						mCommentManager.pause();
						mImageButtonPlay.setImageResource(R.drawable.play);
						break;
					}
					case VLI.INPUT_STATE_ERROR: {
						break;
					}
					default:
						break;
					}
					mCurrentState = msg.arg1;
					break;
				}
				case VLI.EVENT_INPUT_DEAD: {
					break;
				}
				case VLI.EVENT_INPUT_POSITION: {
					int val = msg.arg1 / 1000;
					if (val != mCurrentTime) {
						int hour = val / 3600;
						val -= hour * 3600;
						int minute = val / 60;
						val -= minute * 60;
						int second = val;
						String time = String.format("%02d:%02d:%02d", hour,
								minute, second);
						mTextViewPosition.setText(time);
						mCurrentTime = msg.arg1;
						if (mCurrentLength > 0) {
							mSeekBarProgress.setProgress(mCurrentTime);
						}
					}
					// mCommentManager.seek(msg.arg1);
					break;
				}
				case VLI.EVENT_INPUT_LENGTH: {
					int val = msg.arg1 / 1000;
					int hour = val / 3600;
					val -= hour * 3600;
					int minute = val / 60;
					val -= minute * 60;
					int second = val;
					String time = String.format("%02d:%02d:%02d", hour, minute,
							second);
					mTextViewLength.setText(time);
					mCurrentLength = msg.arg1;
					if (mCurrentLength > 0) {
						mSeekBarProgress.setMax(mCurrentLength);
					}
					break;
				}
				case VLI.EVENT_INPUT_MISC: {
					switch (msg.arg1) {
					case VLI.INPUT_MISC_CAN_PAUSE: {
						mCanPause = msg.arg2;
						break;
					}
					case VLI.INPUT_MISC_CAN_SEEK: {
						mCanSeek = msg.arg2;
						break;
					}
					default:
						break;
					}
					break;
				}
				case VLI.EVENT_VIDEO_SIZE: {
					mVideoOriginalWidth = msg.arg1;
					mVideoOriginalHeight = msg.arg2;
					break;
				}
				case VLI.EVENT_PLAYER_PREPAIRING_BGN: {
					mPrepairDone = false;
					mProgressBarPrepairing.setVisibility(View.VISIBLE);
					break;
				}
				case VLI.EVENT_PLAYER_PREPAIRING_END: {
					mPrepairDone = true;
					mProgressBarPrepairing.setVisibility(View.INVISIBLE);
					break;
				}
				case VLI.EVENT_VIDEO_SURFACE_CREATED: {
					/* this is hopefully called only once */
					break;
				}
				case VLI.EVENT_VIDEO_SURFACE_CHANGED: {
					/* this is hopefully called only once */
					mVideoSurfaceWidth = msg.arg1;
					mVideoSurfaceHeight = msg.arg2;
					mVLC.attachSurface((Surface) msg.obj, mVideoSurfaceWidth,
							mVideoSurfaceHeight);
					try {
						mPrepairLock.lock();
						mVideoSurfaceReady = true;
						mPrepairCond.signal();
					} finally {
						mPrepairLock.unlock();
					}
					break;
				}
				case VLI.EVENT_VIDEO_SURFACE_DESTROYED: {
					/* this is hopefully called only once */
					mVLC.detachSurface();
					break;
				}
				default:
					break;
				}
			}
		};
	}

	protected void initializeControls() {
		mSurfaceViewVideo = (SurfaceView) findViewById(R.id.player_surface_video);
		mSurfaceHolderVideo = mSurfaceViewVideo.getHolder();
		mSurfaceHolderVideo.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				Message msg = new Message();
				msg.what = VLI.EVENT_VIDEO_SURFACE_CREATED;
				msg.obj = holder.getSurface();
				mEventHandler.dispatchMessage(msg);
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				Message msg = new Message();
				msg.what = VLI.EVENT_VIDEO_SURFACE_CHANGED;
				msg.arg1 = width;
				msg.arg2 = height;
				msg.obj = holder.getSurface();
				mEventHandler.dispatchMessage(msg);
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				Message msg = new Message();
				msg.what = VLI.EVENT_VIDEO_SURFACE_DESTROYED;
				mEventHandler.dispatchMessage(msg);
			}
		});
		// mSurfaceViewVideo.setOnTouchListener(this);
		mViewMessage = (View) findViewById(R.id.player_view_message);
		mViewMessage.setBackgroundDrawable(new CommentDrawable());
		mViewMessage.setOnTouchListener(this);

		mTextViewPosition = (TextView) findViewById(R.id.player_text_position);
		mSeekBarProgress = (SeekBar) findViewById(R.id.player_seekbar_progress);
		mSeekBarProgress.setOnSeekBarChangeListener(this);
		mTextViewLength = (TextView) findViewById(R.id.player_text_length);
		mImageButtonAudio = (ImageButton) findViewById(R.id.player_button_audio);
		mImageButtonAudio.setOnClickListener(this);
		mImageButtonSubtitle = (ImageButton) findViewById(R.id.player_button_subtitle);
		mImageButtonSubtitle.setOnClickListener(this);
		mImageButtonPrev = (ImageButton) findViewById(R.id.player_button_prev);
		mImageButtonPrev.setOnClickListener(this);
		mImageButtonPlay = (ImageButton) findViewById(R.id.player_button_play);
		mImageButtonPlay.setOnClickListener(this);
		mImageButtonNext = (ImageButton) findViewById(R.id.player_button_next);
		mImageButtonNext.setOnClickListener(this);
		mImageButtonChat = (ImageButton) findViewById(R.id.player_button_chat);
		mImageButtonChat.setOnClickListener(this);

		mLinearLayoutControlBar = (LinearLayout) findViewById(R.id.player_control_bar);

		mProgressBarPrepairing = (ProgressBar) findViewById(R.id.player_prepairing);
	}

	protected void setMediaSource(String uri) {
		if (mPrepairThread != null && mPrepairThread.isAlive())
			return;
		mUriVideo = uri;
		mUriMessage = uri.substring(0, uri.lastIndexOf(".")) + ".xml";
		mPrepairThread = (new Thread(new Runnable() {
			@Override
			public void run() {
				mEventHandler.sendEmptyMessage(VLI.EVENT_PLAYER_PREPAIRING_BGN);
				// wait until the surface is ready
				mPrepairLock.lock();
				try {
					try {
						while (mVideoSurfaceReady == false) {
							mPrepairCond.await();
						}
					} catch (InterruptedException e) {
					}
				} finally {
					mPrepairLock.unlock();
				}
				mCommentManager.open(mUriMessage);
				mVLM.open(mUriVideo);
				mEventHandler.sendEmptyMessage(VLI.EVENT_PLAYER_PREPAIRING_END);
			}
		}));
		mPrepairThread.start();
	}

	protected void setVideoMode(int mode) {

	}

	@Override
	public void onInputCanPauseChange(boolean value) {
		Message msg = new Message();
		msg.what = VLI.EVENT_INPUT_MISC;
		msg.arg1 = VLI.INPUT_MISC_CAN_PAUSE;
		msg.arg2 = value ? 1 : 0;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onInputCanSeekChange(boolean value) {
		Message msg = new Message();
		msg.what = VLI.EVENT_INPUT_MISC;
		msg.arg1 = VLI.INPUT_MISC_CAN_SEEK;
		msg.arg2 = value ? 1 : 0;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onInputLengthChange(long length) {
		Message msg = new Message();
		msg.what = VLI.EVENT_INPUT_LENGTH;
		msg.arg1 = (int) (length / 1000);
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onInputPositionChange(long position) {
		Message msg = new Message();
		msg.what = VLI.EVENT_INPUT_POSITION;
		msg.arg1 = (int) (position / 1000);
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onInputStateChange(int state) {
		Message msg = new Message();
		msg.what = VLI.EVENT_INPUT_STATE;
		msg.arg1 = state;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onAudioStreamChange(int index, int count) {

	}

	@Override
	public void onSubtitleStreamChange(int index, int count) {

	}

	@Override
	public void onVideoSizeChange(int width, int height) {
		Message msg = new Message();
		msg.what = VLI.EVENT_VIDEO_SIZE;
		msg.arg1 = width;
		msg.arg2 = height;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onSnapshotReady(Bitmap bitmap) {
		Message msg = new Message();
		msg.what = CPI.EVENT_COMMENT_SNAPSHOT;
		msg.obj = bitmap;
		mEventHandler.sendMessage(msg);
	}

	@Override
	public void onVlcEvent(String name, String key, String value) {
		Log.d("faplayer", String.format(
				"unable to process: \"%s\" \"%s\" \"%s\"", name, key, value));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initializeEvents();
		setContentView(R.layout.player);
		initializeControls();
		mVLM.setCallbackHandler(this);
		mCommentManager.setCallbackHandler(this);
		Bundle bundle = getIntent().getExtras().getBundle("playlist");
		mPlayList = bundle.getStringArrayList("list");
		mCurrentIndex = bundle.getInt("index");

		if (mPlayList != null && mCurrentIndex >= 0
				&& mCurrentIndex < mPlayList.size()) {
			String uri = mPlayList.get(mCurrentIndex);
			setMediaSource(uri);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mCommentManager.close();
		mVLM.close();
	}

	@Override
	public void onStart() {
		super.onStart();
		mCommentManager.play();
		mVLM.play();
	}

	@Override
	public void onStop() {
		super.onStop();
		mCommentManager.pause();
		mVLM.pause();
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (!mPrepairDone) {
			return false;
		}
		int action = event.getAction();
		if (action == MotionEvent.ACTION_DOWN) {
			int visibility = mLinearLayoutControlBar.getVisibility();
			if (visibility != View.VISIBLE) {
				mLinearLayoutControlBar.setVisibility(View.VISIBLE);
			} else {
				mLinearLayoutControlBar.setVisibility(View.GONE);
			}
			return true;
		}
		return false;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
		case R.id.player_button_audio: {
			break;
		}
		case R.id.player_button_subtitle: {
			break;
		}
		case R.id.player_button_prev: {
			if (mCurrentIndex != -1 && mPlayList != null
					&& mPlayList.size() > 1) {
				mCurrentIndex--;
				if (mCurrentIndex < 0)
					mCurrentIndex = 0;
				String uri = mPlayList.get(mCurrentIndex);
				setMediaSource(uri);
			}
			break;
		}
		case R.id.player_button_play: {
			if (mCanPause > 0) {
				if (mCurrentState == VLI.INPUT_STATE_PLAY)
					mVLM.pause();
				else if (mCurrentState == VLI.INPUT_STATE_PAUSE)
					mVLM.play();
			}
			break;
		}
		case R.id.player_button_next: {
			if (mCurrentIndex != -1 && mPlayList != null
					&& mPlayList.size() > 1) {
				mCurrentIndex++;
				if (mCurrentIndex >= mPlayList.size())
					mCurrentIndex %= mPlayList.size();
				String uri = mPlayList.get(mCurrentIndex);
				setMediaSource(uri);
			}
			break;
		}
		case R.id.player_button_chat: {
			int visibility = mViewMessage.getVisibility();
			mViewMessage
					.setVisibility(visibility == View.VISIBLE ? View.INVISIBLE
							: View.VISIBLE);
			break;
		}
		default:
			break;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		int id = seekBar.getId();
		switch (id) {
		case R.id.player_seekbar_progress: {
			if (mCanSeek > 0) {
				long position = seekBar.getProgress();
				mCommentManager.seek(position);
				mVLM.seek(position * 1000);
			}
			break;
		}
		default:
			break;
		}
	}
}
