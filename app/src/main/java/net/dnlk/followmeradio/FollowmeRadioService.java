package net.dnlk.followmeradio;

import java.io.IOException;

import net.dnlk.followmeradio.utils.Log;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;


public class FollowmeRadioService extends Service implements
		MediaPlayer.OnPreparedListener {

	MediaPlayer mp = null;
	private final IBinder mBinder = new StreamBinder();

	public static final String TAG = "FollowmeRadioService";
	private static final String URL_NEW = "http://m.radiofollow.me:8000/live";
	private static final String URL_HITS = "http://m.radiofollow.me:8001/live";
	private static final String URL_MIXES = "http://m.radiofollow.me:8004/live";
	private static final String URL_RUS = "http://m.radiofollow.me:8002/live";

	public static final int STREAM_NEW = 1;
	public static final int STREAM_HITS = 2;
	public static final int STREAM_MIXES = 3;
	public static final int STREAM_RUS = 4;

	public static final String ACTION_PLAY = "PLAY_STREAM";

	private WifiLock wifiLock; // keep the wifi from turning off
	protected boolean stream_playing = false;
	protected boolean stream_loaded = false;
	protected boolean play_stream_on_prepared = false;
	protected boolean is_notification_shown = false;
	protected boolean is_activity_visible = true;

	int current_bitrate = 192;
	int current_channel = STREAM_HITS;
	private NotificationCompat.Builder n;
	private NotificationManager mNotifyMgr;

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	public class StreamBinder extends Binder {
		FollowmeRadioService getService() {
			// Return this instance of LocalService so clients can call public
			// methods
			return FollowmeRadioService.this;
		}
	}

	@Override
	public void onCreate() {
		initStream();
		super.onCreate();

	}

	@Override
	public void onDestroy() {
		stopNotification();
		super.onDestroy();

	}

	@Override
	public void onPrepared(MediaPlayer arg0) {

		Log.i(TAG, "stream prepared");

		mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

		/*
		 * Aquire a wifi lock to protect against unexpected stopage of the
		 * stream
		 */
		wifiLock = ((WifiManager) getApplicationContext().getSystemService(
				Context.WIFI_SERVICE)).createWifiLock(
				WifiManager.WIFI_MODE_FULL, "mylock");
		wifiLock.acquire();

		stream_loaded = true;

		if (play_stream_on_prepared) {
			playStream();
			play_stream_on_prepared = false;
		}

		// Send broadcast to PlayerActivity to update track info and
		// "loading..." text
		sendBroadcast(new Intent().setAction("steam_prepared"));

	}

	public void switchChannel(int channel) {
		this.current_channel = channel;

		initStream();

		Log.v(TAG, "Switched to channel: " + channel);
	}

	public void switchBitrate(int bitrate) {
		this.current_bitrate = bitrate;
		initStream();
		Log.v(TAG, "Switched bitrate: " + bitrate);
	}

	/* Initialize the radio stream */

	public void initStream() {

		initStream(false);
	}

	public void initStream(boolean play_on_prepared) {
		if (isLoaded() && isPlaying()) {
			stopStream();
			play_stream_on_prepared = true;

		}
		if (play_on_prepared)
			play_stream_on_prepared = true;

		if (mp != null)
			mp.release();
		mp = new MediaPlayer();
		mp.setOnPreparedListener(this);
		mp.setAudioStreamType(AudioManager.STREAM_MUSIC);

		try {
			switch (this.current_channel) {
			case STREAM_NEW:
				mp.setDataSource(URL_NEW + current_bitrate);
				break;
			case STREAM_HITS:
				mp.setDataSource(URL_HITS + current_bitrate);
				break;
			case STREAM_MIXES:
				mp.setDataSource(URL_MIXES + current_bitrate);
				break;
			case STREAM_RUS:
				mp.setDataSource(URL_RUS + current_bitrate);
				break;
			default:
				mp.setDataSource(URL_HITS + current_bitrate);
				break;

			}
			Log.v(TAG, "Initialized stream. Channel: [" + current_channel
					+ "] bitrate: [" + current_bitrate + "]");

		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		mp.prepareAsync(); // prepare async to not block main thread
	}

	/*
	 * public void pauseStream() { mp.pause(); stopNotification(); }
	 */

	public void playStream() {
		mp.start();
		setNotification();
	}

	public void stopStream() {
		mp.stop();
		stopNotification();
	}

	public boolean isPlaying() {
		return mp.isPlaying();
	}

	public boolean isLoaded() {
		return stream_loaded;
	}

	public void releaseStream() {
		stopNotification();
		if (mp != null)
			mp.release();
		mp = null;
		stream_loaded = false;
		try {
			wifiLock.release();
			Log.i(TAG, "wifiLock released");
		} catch (Exception e) {
			Log.e(TAG, "Problem releasing wifiLock: " + e.toString());
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// All clients have unbound with unbindService()
		releaseStream();
		return false;
	}

	public Integer getCurrentChannel() {
		return current_channel;
	}

	public void setNotification() {

		n = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_letters)
				.setLargeIcon(
						BitmapFactory.decodeResource(getResources(),
								R.drawable.logo_notif))
				.setContentTitle("Радио Follow Me");

		// onClick
		Intent i = new Intent(this, MainActivity.class);
		PendingIntent pIntent = PendingIntent.getActivity(this, 0, i, 0);
		n.setContentIntent(pIntent);

		n.setOngoing(true);

		mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNotifyMgr.notify(001, n.build());

		is_notification_shown = true;
	}

	public void updateNotification(String text) {
		n.setContentText(text);
		if (mNotifyMgr != null) {
			mNotifyMgr.notify(001, n.build());
			is_notification_shown = true;
		}
	}

	public void stopNotification() {
		if (mNotifyMgr != null)
			mNotifyMgr.cancel(001);
		is_notification_shown = false;

	}

	public void setActivityVisible(boolean b) {
		is_activity_visible = b;
	}

	public boolean isActivityVisible() {

		return is_activity_visible;
	}

	public boolean isNotificationShown() {

		return is_notification_shown;
	}

}
