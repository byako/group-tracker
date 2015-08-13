package org.byako.group_tracker;

import android.os.Handler;

public class TrackerService extends Thread {
	private Handler parentHandler = null;
	
	public TrackerService(Handler pHandler) {
		parentHandler = pHandler;
	}
	
	
	
}
