package net.dnlk.followmeradio;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import net.dnlk.followmeradio.utils.Log;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.AsyncTask;

public class CoverTracknameAsynctask extends AsyncTask<Integer, Void, String[]> {

	public CoverTrackAsyncResponse d = null;

	private static final String URL_SONGNAMES = "http://radiofollow.me/track_name.txt";
	private static final String URL_COVER = "http://radiofollow.me/cover?track=";
	public static final int STREAM_NEW = 1;
	public static final int STREAM_HITS = 2;
	public static final int STREAM_MIXES = 3;
	public static final int STREAM_RUS = 4;
	private String TAG = "CoverTracknameAsynctask";

	@Override
	protected String[] doInBackground(Integer... channel) {
		int ch = channel[0];
		ArrayList<String> track_names = new ArrayList<String>();
		String[] current_track = new String[2];
		try {
			URL url = new URL(URL_SONGNAMES);

			// Read text returned by the server
			BufferedReader in = new BufferedReader(new InputStreamReader(
					url.openStream()));
			String str;
			while ((str = in.readLine()) != null) {
				track_names.add(str);
			}
			in.close();

			switch (ch) {
			case STREAM_NEW:
				current_track[0] = track_names.get(0);
				break;
			case STREAM_HITS:
				current_track[0] = track_names.get(1);
				break;
			case STREAM_MIXES:
				current_track[0] = track_names.get(3);
				break;
			case STREAM_RUS:
				current_track[0] = track_names.get(2);
				break;

			}

			// get cover direct url
			HttpClient httpclient = new DefaultHttpClient();
			HttpResponse response = httpclient.execute(new HttpGet(URL_COVER
					+ URLEncoder.encode(current_track[0], "UTF-8")));
			StatusLine statusLine = response.getStatusLine();
			if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				response.getEntity().writeTo(out);
				out.close();
				current_track[1] = out.toString();

			} else {
				response.getEntity().getContent().close();
				throw new IOException(statusLine.getReasonPhrase());
			}

		} catch (MalformedURLException e) {
		} catch (IOException e) {
		}

		return current_track;

	}

	@Override
	protected void onPostExecute(String[] current_track) {
		d.finishCoverTrackUpdate(current_track);
		Log.v(TAG, "resolved track name: " + current_track[0]);
		Log.v(TAG, "resolved track cover: " + current_track[1]);

	}

	@Override
	protected void onPreExecute() {
	}

	@Override
	protected void onProgressUpdate(Void... values) {
	}
}