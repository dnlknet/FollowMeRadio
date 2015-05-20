package net.dnlk.followmeradio;

import java.util.Timer;
import java.util.TimerTask;

import net.dnlk.followmeradio.FollowmeRadioService.StreamBinder;
import net.dnlk.followmeradio.utils.Log;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

public class PlayerFragment extends Fragment implements CoverTrackAsyncResponse {

	boolean mBound = false;

	public FollowmeRadioService mService;
	protected int current_volume;
	public SeekBar volumeControl;
	private ImageView playPauseButton;
	private TextView track_title;
	private ImageView track_cover;
	private CoverTracknameAsynctask mCoverTrackAsyncTask;
	private android.app.AlertDialog offlineDialog;
	private String TAG = "PlayerFragment";
	private TextView track_artist;
	public ConnectivityManager cm;
	private TimerTask networkCheckTask;
	private Timer networkChecktimer;
	private FollowMeRadioServiceReceiver mReceiver;
	public boolean networkCheckScheduleStarted = false;
	public View mView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);

		// BroadcastReceiver to receive events from service
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("steam_prepared");
		mReceiver = new FollowMeRadioServiceReceiver();
		getActivity().registerReceiver(mReceiver, intentFilter);

		// Bind to Service
		Intent intent = new Intent(getActivity(), FollowmeRadioService.class)
				.setAction(FollowmeRadioService.ACTION_PLAY);
		getActivity().getApplicationContext().bindService(intent, mConnection,
				Context.BIND_AUTO_CREATE);
		getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

		cm = (ConnectivityManager) getActivity().getSystemService(
				Context.CONNECTIVITY_SERVICE);

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		return inflater.inflate(R.layout.fragment_player, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle b) {

		mView = view;

		InitChannelButtons();
		InitBitrateButtons();
		InitPlayPauseButton(mView);
		InitVolumeControl(mView);

		initCoverUpdateSchedule();

	}

	@Override
	public void onResume() {
		if ((mView != null) && (mService != null))
			if (mService.isLoaded()) {
				if (mService.isPlaying())
					playPauseButton.setImageDrawable(getResources()
							.getDrawable(R.drawable.ic_pause));
				else
					playPauseButton.setImageDrawable(getResources()
							.getDrawable(R.drawable.ic_play));
			}

		if (!networkEnabled(cm))
			startNetworkCheckSchedule();

		// activity visible
		if (mService != null)
			mService.setActivityVisible(true);

		super.onResume();
	}

	@Override
	public void onPause() {

		// activity visible
		if (mService != null)
			mService.setActivityVisible(false);

		super.onPause();
	}

	private void InitPlayPauseButton(View v) {
		playPauseButton = (ImageView) v.findViewById(R.id.button_PlayPause);
		playPauseButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				playPause();
			}
		});

	}

	public void playPause() {
		// Show a toast if the stream is still loading
		if (!mService.isLoaded()) {
			mService.initStream(true);
			playPauseButton.setImageDrawable(getResources().getDrawable(
					R.drawable.ic_pause));
			updateCover();
		} else if (mService.isPlaying()) {
			mService.releaseStream();
			playPauseButton.setImageDrawable(getResources().getDrawable(
					R.drawable.ic_play));
			updateCover();
			// otherwise play the stream
		} else {
			mService.initStream(true);

			playPauseButton.setImageDrawable(getResources().getDrawable(
					R.drawable.ic_pause));
			updateCover();

		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "onDestroy");
		try {

			getActivity().unregisterReceiver(mReceiver);
			mService.stopNotification();
		} catch (Exception e) {

			e.printStackTrace();
		}

		if (mBound) {
			// unbind the mService before ending
			getActivity().getApplicationContext().unbindService(mConnection);
			mBound = false;
		}

	}

	@Override
	public void onStop() {
		super.onStop();

	}

	/* Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {

			StreamBinder binder = (StreamBinder) service;
			mService = binder.getService();
			mBound = true;

		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBound = false;
		}

	};

	private void InitChannelButtons() {
		RadioGroup channelButtons = (RadioGroup) getActivity().findViewById(
				R.id.channelButtons);
		final RadioButton btn_new = (RadioButton) getActivity().findViewById(
				R.id.btn_new);
		final RadioButton btn_hits = (RadioButton) getActivity().findViewById(
				R.id.btn_hits);
		final RadioButton btn_mixes = (RadioButton) getActivity().findViewById(
				R.id.btn_mixes);
		final RadioButton btn_rus = (RadioButton) getActivity().findViewById(
				R.id.btn_rus);
		channelButtons
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					public void onCheckedChanged(RadioGroup group, int checkedId) {

						if (btn_new.isChecked()) {

							mService.switchChannel(1);
						}

						else if (btn_hits.isChecked()) {

							mService.switchChannel(2);

						} else if (btn_mixes.isChecked()) {

							mService.switchChannel(3);

						} else if (btn_rus.isChecked()) {

							mService.switchChannel(4);

						}
						updateCover();
					}
				});
	}

	private void InitBitrateButtons() {
		RadioGroup bitrateButtons = (RadioGroup) getActivity().findViewById(
				R.id.bitrateButtons);
		final RadioButton btn_32 = (RadioButton) getActivity().findViewById(
				R.id.btn_32);
		final RadioButton btn_64 = (RadioButton) getActivity().findViewById(
				R.id.btn_64);
		final RadioButton btn_128 = (RadioButton) getActivity().findViewById(
				R.id.btn_128);
		final RadioButton btn_192 = (RadioButton) getActivity().findViewById(
				R.id.btn_192);
		bitrateButtons
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					public void onCheckedChanged(RadioGroup group, int checkedId) {

						if (btn_32.isChecked()) {
							if ((mService.isLoaded()))
								mService.switchBitrate(32);

						}

						else if (btn_64.isChecked()) {

							if ((mService.isLoaded()))
								mService.switchBitrate(64);

						} else if (btn_128.isChecked()) {

							if ((mService.isLoaded()))
								mService.switchBitrate(128);

						} else if (btn_192.isChecked()) {

							if ((mService.isLoaded()))
								mService.switchBitrate(192);

						}
					}
				});
	}

	private void InitVolumeControl(View v) {
		volumeControl = (SeekBar) v.findViewById(R.id.volume);
		AudioManager audioManager = (AudioManager) getActivity()
				.getSystemService(Context.AUDIO_SERVICE);
		volumeControl.setMax(audioManager
				.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
		volumeControl.setProgress(audioManager
				.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
		volumeControl.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// Not much
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// Not much
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				changeVolume(progress);
				current_volume = progress;

			}
		});

	}

	public void changeVolume(int volume) {
		AudioManager audioManager = (AudioManager) getActivity()
				.getSystemService(Context.AUDIO_SERVICE);
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);

	}

	private void initCoverUpdateSchedule() {
		final Handler handler = new Handler();
		Timer timer = new Timer();
		TimerTask doAsynchronousTask = new TimerTask() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					public void run() {
						try {

							mCoverTrackAsyncTask = new CoverTracknameAsynctask();
							mCoverTrackAsyncTask.d = PlayerFragment.this;
							if (mService.isLoaded() && (mService.isPlaying()))
								mCoverTrackAsyncTask.execute(mService
										.getCurrentChannel());
						} catch (Exception e) {
							Log.v("TimerTask", e.toString());
						}
					}
				});
			}
		};
		timer.schedule(doAsynchronousTask, 0, 25000);

	}

	private void startNetworkCheckSchedule() {
		if (networkEnabled(cm) || networkCheckScheduleStarted)
			return;
		offlineDialog = new AlertDialog.Builder(getActivity())
				.setTitle(getResources().getString(R.string.noconnection))
				.setMessage(getResources().getString(R.string.whatwithinternet))
				.setCancelable(false).create();

		offlineDialog.show();

		final Handler handler = new Handler();
		networkChecktimer = new Timer();
		networkCheckTask = new TimerTask() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					public void run() {
						try {

							// Check connection
							Log.v(TAG, "checking network...");
							if (networkEnabled(cm)) {

								offlineDialog.dismiss();
								networkChecktimer.cancel();
								networkCheckTask.cancel();
								mService.initStream();
								networkCheckScheduleStarted = false;
							}

						} catch (Exception e) {
							Log.v("NetworkCheckTask", e.toString());
						}
					}
				});
			}
		};
		if (!networkEnabled(cm)) {
			networkChecktimer.schedule(networkCheckTask, 0, 1000);
			networkCheckScheduleStarted = true;
		}

	}

	private void updateCover() {

		mCoverTrackAsyncTask = new CoverTracknameAsynctask();
		mCoverTrackAsyncTask.d = this;
		mCoverTrackAsyncTask.execute(mService.getCurrentChannel());
	}

	@Override
	public void finishCoverTrackUpdate(String[] current_track) {

		try {
			track_artist = (TextView) getActivity().findViewById(
					R.id.trackArtist);
			track_title = (TextView) getActivity()
					.findViewById(R.id.trackTitle);
			track_cover = (ImageView) getActivity().findViewById(
					R.id.albumImage);

			String[] trackTitle = current_track[0].split("-");

			track_artist.setText(trackTitle[0].trim());// Artist
			track_title.setText(trackTitle[1].trim()); // Title

			if (mService.isActivityVisible()) {
				if (current_track[1].length() > 0)
					Picasso.with(getActivity().getBaseContext())
							.load(current_track[1]).into(track_cover);
				else
					track_cover.setImageDrawable(getResources().getDrawable(
							R.drawable.album));
			}

			if (mService.isNotificationShown())
				mService.updateNotification(current_track[0].trim());

		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}

	}

	protected static boolean networkEnabled(ConnectivityManager connec) {
		// ARE WE CONNECTED TO THE NET

		if (connec == null) {
			return false;
		}

		try {
			if (connec.getNetworkInfo(1) != null
					&& connec.getNetworkInfo(1).getState() == NetworkInfo.State.CONNECTED)
				return true;
			else if (connec.getNetworkInfo(0) != null
					&& connec.getNetworkInfo(0).getState() == NetworkInfo.State.CONNECTED)
				return true;
			else
				return false;
		} catch (NullPointerException exception) {
			return false;
		}
	}

	public void setVolumeControlValue(int new_volume) {
		volumeControl.setProgress(new_volume);

	}

	private class FollowMeRadioServiceReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context arg0, Intent arg1) {

			updateCover();

		}

	}

}
