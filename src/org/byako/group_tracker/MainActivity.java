package org.byako.group_tracker;

import org.byako.R;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity implements
		GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

	private GoogleApiClient mGoogleApiClient;
	private Location mLastLocation;
	private boolean isStarted;
	private String attendeeName;
	private String eventName;
	private Location mCurrentLocation;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(LocationServices.API)
				.build();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
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
		setLocationText("Updating location...");
/*		if (mLastLocation != null) {
			setLocationText(String.valueOf(mLastLocation.getLatitude()) + ":" + String.valueOf(mLastLocation.getLongitude()));
		} else {
			setStatusText("ERROR getting last location");
		}*/
	}

	@Override
	public void onConnectionSuspended(int reason) {
		setStatusText("connection suspended");
	}
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		setStatusText("connection failed");
	}

	private void setStatusText(String newLabel) {
		((TextView)findViewById(R.id.statusViewId)).setText(newLabel);
	}
	private void setLocationText(String newLabel) {
		((TextView)findViewById(R.id.locationViewId)).setText(newLabel);
	}

	public void onStartStopButtonClicked(View v) {
		//((TextView)findViewById(R.id.statusViewId)).setText("Started");
		if (!isStarted) {
			mGoogleApiClient.connect();
			attendeeName = findViewById(R.id.attendeeName).toString();
			findViewById(R.id.attendeeName).setEnabled(false);
			eventName = findViewById(R.id.eventName).toString();
			findViewById(R.id.eventName).setEnabled(false);

			isStarted = true;
			setStatusText("Started");
		} else {
			if (mGoogleApiClient.isConnected()) {
				LocationServices.FusedLocationApi.removeLocationUpdates(
						mGoogleApiClient, this);
				mGoogleApiClient.disconnect();
			}
			findViewById(R.id.attendeeName).setEnabled(true);
			findViewById(R.id.eventName).setEnabled(true);
			isStarted = false;
			setStatusText("Stopped");
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		mCurrentLocation = location;
//		mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
		setLocationText(location.getLatitude() + " : " + location.getLongitude());
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mGoogleApiClient.isConnected()) {
			LocationServices.FusedLocationApi.removeLocationUpdates(
					mGoogleApiClient, this);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
//		if (mGoogleApiClient.isConnected() && isStarted) {
//			startLocationUpdates();
//		}
	}
}