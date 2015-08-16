package org.byako.group_tracker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class TrackerService extends Service  implements
		GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
	/* post data */
	private String attendeeName = "anonymous";
	private String eventName = "efar15";
	private String serverURL = "http://byako.org/google_map/new_data.php";

	/* debug */
	private boolean isDebug = false;
	private void tsLog (String s) {
		if (isDebug)
			Log.i("TrackerServiceDebug", s);
	}

	/* power management */
	private PowerManager.WakeLock gpsWakeLock = null;
	private PowerManager gpsPM = null;

	/* GPS -related vars */
	private GoogleApiClient mGoogleApiClient;
	private Location mLastLocation;
	private boolean isStarted;
	private Location mCurrentLocation;
	private boolean isConnected;

	/* IPC related vars */
	private Messenger replyTo;
	final Handler hdlr = new IncomingHandler();
	final Messenger msgr = new Messenger(hdlr);

	static final int MSG_REGISTER_CLIENT_COMMAND = 0;
	static final int MSG_REGISTER_CLIENT_RESPONSE = 1;
	static final int MSG_UNREGISTER_CLIENT_COMMAND = 2;
	static final int MSG_LOCATION_UPDATE_RECEIVED = 3;
	static final int MSG_SERVICE_STATUS_REQUEST = 4;
	static final int MSG_SERVICE_STATUS_RESPONSE = 5;
	static final int MSG_GPS_ENABLED = 6;
	static final int MSG_GPS_DISABLED = 7;
	static final int MSG_STOP_GPS_POLLING = 8;
	static final int MSG_SET_DATA = 9;

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			try {
				Log.i("TrackerServiceHandler", "Service got message:" + msg.what);
				switch (msg.what) {
					case MSG_REGISTER_CLIENT_COMMAND:
						replyTo = msg.replyTo;
						replyTo.send(Message.obtain(null, MSG_REGISTER_CLIENT_RESPONSE, 0, 0));
						break;
					case MSG_UNREGISTER_CLIENT_COMMAND:
						replyTo = null;
						break;
					case MSG_SERVICE_STATUS_REQUEST:
						Message rmsg = Message.obtain(null, MSG_SERVICE_STATUS_RESPONSE, isStarted ? 1 : 0, 0);
						Bundle bndl = new Bundle();
						bndl.putString("attendeeName", attendeeName);
						bndl.putString("eventName", eventName);
						rmsg.setData(bndl);
						replyTo.send(rmsg);
						break;
					case MSG_STOP_GPS_POLLING:
						stopGPSPolling();
						/* this should be the same and only place where we know we were stopped gracefully */
						isStarted = false;
						if (replyTo != null) {
							try {
								replyTo.send(Message.obtain(null, MSG_SERVICE_STATUS_RESPONSE, 0, 0));
							} catch (RemoteException e) {
								Log.i("TrackerServiceHandler", "Failed to send MSG_SERVICE_STATUS_RESPONSE:" + e);
							}
						}
						break;
					case MSG_SET_DATA:
						eventName = msg.getData().getString("eventName");
						attendeeName = msg.getData().getString("attendeeName");
						break;
					default:
						Log.i("TrackerServiceHandler", "Service got unknown message:" + msg.what);
				}
			} catch (Exception e) {
				Log.i("TrackerServiceHandler", "Failed processing msg: " + e + "; msg.what: " + msg.what);
			}
		}
	}

	@Override
	public void onConnected(Bundle connectionHint) {
		mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
		sendLocationToActivity(mLastLocation);
		uploadLocation(mLastLocation);
		LocationRequest mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(10000);
		mLocationRequest.setFastestInterval(5000);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		LocationServices.FusedLocationApi.requestLocationUpdates(
				mGoogleApiClient, mLocationRequest, this);

		isConnected = true;
		if (replyTo != null) {
			try {
				replyTo.send(Message.obtain(null, MSG_GPS_ENABLED, 0, 0));
			} catch (Exception e) {
				tsLog("Failed sending MSG_GPS_ENABLED message:" + e);
			}
		}
	}

	@Override
	public void onConnectionSuspended(int reason) {
		isConnected = false;
		if (replyTo != null) {
			try {
				replyTo.send(Message.obtain(null, MSG_GPS_DISABLED, 0, 0));
			} catch (Exception e) {
				tsLog("Failed sending MSG_GPS_DISABLED message:" + e);
			}
		}
	}
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		isConnected = false;
		if (replyTo != null) {
			try {
				replyTo.send(Message.obtain(null, MSG_GPS_DISABLED, 0, 0));
			} catch (Exception e) {
				tsLog("Failed sending MSG_GPS_DISABLED message:" + e);
			}
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(LocationServices.API)
				.build();

		tsLog("Service created, Google API is built.");
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return msgr.getBinder();
	}

	@Override
	public void onRebind(Intent arg0) {
		tsLog("service is rebound");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		gpsPM = (PowerManager) getSystemService(Context.POWER_SERVICE);
		gpsWakeLock = gpsPM.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrackerService");
		startGPSPolling();
		isStarted = true;
		if (replyTo!=null) {
			try {
				replyTo.send(Message.obtain(null, MSG_SERVICE_STATUS_RESPONSE, 1, 0));
			} catch (RemoteException e) {
				tsLog("Could not send MSG_SERVICE_STATUS_RESPONSE message:" + e);
			}
		}
		tsLog("starting service");
		return START_STICKY;
	}

	@Override
	public boolean onUnbind(Intent arg0) {
		if (replyTo == null && isStarted == false) {
			if (isConnected) { // shouoldl not be here normaly
				stopGPSPolling();
			}
			stopSelf();
		}
		return true;
	}

	@Override
	public void onDestroy() {
		tsLog("service destroyed");
		if (mGoogleApiClient.isConnected()) {
			LocationServices.FusedLocationApi.removeLocationUpdates(
					mGoogleApiClient, this);
		}
		super.onDestroy();
	}

	@Override
	public void onLocationChanged(Location location) {
		tsLog("location changed: lg:" + location.getLongitude() + "; lt:" + location.getLatitude());
		mCurrentLocation = location;

		sendLocationToActivity(mCurrentLocation);
//		mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
		uploadLocation(mCurrentLocation);
	}

	private void sendLocationToActivity(Location location) {
		if (replyTo != null) {
			try {
				Bundle loc = new Bundle();
				loc.putDouble("latitude", location.getLatitude());
				loc.putDouble("longitude", location.getLongitude());
				Message locMessage = Message.obtain(null, MSG_LOCATION_UPDATE_RECEIVED, 0, 0);
				locMessage.setData(loc);
				replyTo.send(locMessage);
			} catch (Exception e) {
				tsLog("Failed sending MSG_LOCATION_UPDATE_RECEIVED message:" + e);
			}
		}
	}

	private void startGPSPolling() {
		tsLog("starting GPS polling");
		mGoogleApiClient.connect();
	}

	private void stopGPSPolling() {
		tsLog("stoping GPS polling");
		if (gpsWakeLock != null && gpsWakeLock.isHeld()) {
			gpsWakeLock.release();
		}
		if (mGoogleApiClient.isConnected()) {
			LocationServices.FusedLocationApi.removeLocationUpdates(
					mGoogleApiClient, this);
			mGoogleApiClient.disconnect();
		}
	}

	private void uploadLocation(Location location) {
		Bundle pack = new Bundle();
		pack.putDouble("latitude", location.getLatitude());
		pack.putDouble("longitude", location.getLongitude());
		pack.putString("attendeeName", attendeeName);

		Uploader u = new Uploader();
		u.execute(pack);
	}


	class Uploader extends AsyncTask<Bundle, Void, Void> {
		@Override
		protected Void doInBackground(Bundle... pack) {
			Exception ee = null;
			HttpURLConnection connection;
			OutputStreamWriter request = null;

			URL url = null;
			String response = null;
			String parameters = "data=new_data&longitude="+ pack[0].getDouble("longitude")+"&latitude="+pack[0].getDouble("latitude")+"&name=" + pack[0].getString("attendeeName");

			try
			{
				tsLog("sending location to server");
				url = new URL(serverURL);
				connection = (HttpURLConnection) url.openConnection();
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				connection.setRequestMethod("POST");

				request = new OutputStreamWriter(connection.getOutputStream());
				request.write(parameters);
				request.flush();
				request.close();
				String line = "";
				InputStreamReader isr = new InputStreamReader(connection.getInputStream());
				BufferedReader reader = new BufferedReader(isr);
				StringBuilder sb = new StringBuilder();
				while ((line = reader.readLine()) != null)
				{
					sb.append(line + "\n");
				}
				// Response from server after login process will be stored in response variable.
				response = sb.toString();
				// You can perform UI operations here
//				Toast.makeText(this, "Message from Server: \n" + response, 0).show();
				tsLog("Server response: " + response);
				isr.close();
				reader.close();
			}
			catch(Exception e)
			{
				tsLog("Failed to upload location:"+e);
			}
			return null;
		}
	}
}