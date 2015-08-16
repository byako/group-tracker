package org.byako.group_tracker;

import org.byako.group_tracker.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
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

public class MainActivity extends Activity {

	private String attendeeName;
	private String eventName;
	private Boolean isStarted;
	private Boolean isDebug = true;

	/* IPC related stuff, to talk to service polling GPS */
	Messenger tsService = null;
	final Messenger tsMessenger = new Messenger (new IncomingHandler());
	private boolean isBound = false;

	private ServiceConnection tsConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.i("MainActivity", "Service connected");
			tsService = new Messenger(service);
			isBound = true;
			try {
				Message msg = Message.obtain(null, TrackerService.MSG_REGISTER_CLIENT_COMMAND);
				msg.replyTo = tsMessenger;
				tsService.send(msg);
			} catch (RemoteException e) {
				Log.i("MainActivity", "Exception: " + e);
			}
		}
		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.i("MainACtivity", "Service disconnected");
			isBound = false;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (!isBound) {
			bindService(new Intent(this, TrackerService.class), tsConnection, Context.BIND_AUTO_CREATE);
		}
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


	private void setStatusText(String newLabel) {
		((TextView)findViewById(R.id.statusViewId)).setText(newLabel);
	}
	private void setLocationText(String newLabel) {
		((TextView)findViewById(R.id.locationViewId)).setText(newLabel);
	}

	public void onStartStopButtonClicked(View v) {
		//((TextView)findViewById(R.id.statusViewId)).setText("Started");
		if (!isStarted) {
			// TODO: send message to serevice to start polling

			attendeeName = findViewById(R.id.attendeeName).toString();
			eventName = findViewById(R.id.eventName).toString();

			findViewById(R.id.attendeeName).setEnabled(false);
			findViewById(R.id.eventName).setEnabled(false);
			// TODO: move status change to handler
			startService(new Intent(this, TrackerService.class));
		} else {
			// TODO: send message to service to stop polling
			findViewById(R.id.attendeeName).setEnabled(true);
			findViewById(R.id.eventName).setEnabled(true);

			// TODO: move status change to handler
			if (!isStarted)
				maLog("STOPPING NON-RUNNIGN SERVICE");
			stopService(new Intent(this, TrackerService.class));
		}
	}


	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onStop() {
		if (isBound) {
			if (tsService != null) {
				try {
					Message msg = Message.obtain(null, TrackerService.MSG_UNREGISTER_CLIENT_COMMAND);
					tsService.send(msg);
				} catch (RemoteException e) {
					Log.i("MainActivity", "Could not send unbind message in onStop");
				}
			}
			unbindService(tsConnection);
			isBound = false;
		}
		super.onStop();
	}

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
				case TrackerService.MSG_LOCATION_UPDATE_RECEIVED:
				 	//	setLocationText(location.getLatitude() + " : " + location.getLongitude());
					break;
				case TrackerService.MSG_SERVICE_STATUS_RESPONSE:
					if (msg.arg1 == 1 ? true : false) {
							isStarted = true;
							setStatusText("Started");
					} else {
							isStarted = false;
							setStatusText("Stopped");
					}
					break;
				case TrackerService.MSG_REGISTER_CLIENT_RESPONSE:
					Log.i("MainActivityHandler", "service client registered, requesting service status");
					Message rmsg = Message.obtain(null, TrackerService.MSG_SERVICE_STATUS_REQUEST);
					try {
						tsService.send(rmsg);
					} catch (RemoteException e) {
						Log.i("MainActivityHandler", "Could not send message to tracker service");
					}
					break;
				default:
					Log.i("MainActivityHandler", "Unknown message received:" + msg.what);
			}
		}
	}

	private void maLog(String s) {
		if (isDebug) {
			Log.i("MainActivity", s);
		}
	}
}