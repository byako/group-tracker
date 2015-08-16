package org.byako.group_tracker;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class TrackerService extends Service  implements
		GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
	/* post data */
	String attendeeName;
	String eventName;

	/* debug */
	private boolean isDebug = true;
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
						replyTo.send(Message.obtain(null, MSG_SERVICE_STATUS_RESPONSE, isStarted ? 1 : 0, 0));
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
//		mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
		LocationRequest mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(10000);
		mLocationRequest.setFastestInterval(5000);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		LocationServices.FusedLocationApi.requestLocationUpdates(
				mGoogleApiClient, mLocationRequest, this);

		// TODO: replace with message to activity
		// setLocationText("Updating location...");

/*		if (mLastLocation != null) {
			setLocationText(String.valueOf(mLastLocation.getLatitude()) + ":" + String.valueOf(mLastLocation.getLongitude()));
		} else {
			setStatusText("ERROR getting last location");
		}*/
	}

	@Override
	public void onConnectionSuspended(int reason) {
		// TODO: replace with message to activity
//		setStatusText("connection suspended");
	}
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		// TODO: replace with message to activity
//		setStatusText("connection failed");
	}

	@Override
	public void onCreate() {
		super.onCreate();

		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(LocationServices.API)
				.build();

		tsLog("Service created");
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
		isStarted = true;
		tsLog("starting service");
		return START_STICKY;
	}

	@Override
	public boolean onUnbind(Intent arg0) {
		if (replyTo == null && isStarted == false) {
			// stop polling GPS
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
//		mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
		// TODO: send message to activity if it's bound
		// TODO: send post message to server
	}

	private void startGPSPolling() {
		tsLog("starting GPS polling");
		mGoogleApiClient.connect();

		isStarted = true;
	}

	private void stopGPSPolling() {
		tsLog("stoping GPS polling");
		if (mGoogleApiClient.isConnected()) {
			LocationServices.FusedLocationApi.removeLocationUpdates(
					mGoogleApiClient, this);
			mGoogleApiClient.disconnect();
		}
	}
}