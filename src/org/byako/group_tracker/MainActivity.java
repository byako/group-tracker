package org.byako.group_tracker;

import org.byako.R;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends Activity implements
		GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

	private GoogleApiClient mGoogleApiClient;
	private Location mLastLocation;

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
		mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
		if (mLastLocation != null) {
			setStatusText(String.valueOf(mLastLocation.getLatitude()) + ":" + String.valueOf(mLastLocation.getLongitude()));
		} else {
			setStatusText("ERROR getting last location");
		}
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

	public void onStartButtonClicked(View v) {
		//((TextView)findViewById(R.id.statusViewId)).setText("Started");
		mGoogleApiClient.connect();
	}

	public void onStopButtonClicked(View v) {
		//((TextView)findViewById(R.id.statusViewId)).setText("Stopped");
		mGoogleApiClient.disconnect();
	}
}