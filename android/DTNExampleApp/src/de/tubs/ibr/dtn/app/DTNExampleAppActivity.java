package de.tubs.ibr.dtn.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import de.tubs.ibr.dtn.api.Block;
import de.tubs.ibr.dtn.api.BundleID;
import de.tubs.ibr.dtn.api.CallbackMode;
import de.tubs.ibr.dtn.api.DTNClient;
import de.tubs.ibr.dtn.api.DataHandler;
import de.tubs.ibr.dtn.api.Registration;
import de.tubs.ibr.dtn.api.ServiceNotAvailableException;
import de.tubs.ibr.dtn.api.SessionDestroyedException;
import de.tubs.ibr.dtn.app.R;

public class DTNExampleAppActivity extends Activity {
	
	private final static String TAG = "DTNExampleAppActivity";
	
	// executor to process local job queue
	private ExecutorService _executor = null;
	
	// DTN client to talk with the DTN service
	private LocalDTNClient _client = null;
	
	private class LocalDTNClient extends DTNClient {
		
		public LocalDTNClient() {
			super(getApplicationInfo().packageName);
		}

		@Override
		protected void sessionConnected(Session session) {
			Log.d(TAG, "DTN session connected");
			
	        // check for bundles first
	        _executor.execute(_query_task);
		}

		@Override
		protected CallbackMode sessionMode() {
			return CallbackMode.FILEDESCRIPTOR;
			//return CallbackMode.SIMPLE;
		}

		@Override
		protected void online() {
			Log.i(TAG, "DTN is online.");
		}

		@Override
		protected void offline() {
			Log.i(TAG, "DTN is offline.");
		}
		
	};
	
	private BroadcastReceiver _receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(de.tubs.ibr.dtn.Intent.RECEIVE))
			{
				// RECEIVE intent received, check for new bundles
				_executor.execute(_query_task);
			}
		}
	};

	private Runnable _query_task = new Runnable() {
		@Override
		public void run() {
			try {
				while (_client.query());
			} catch (SessionDestroyedException e) {
				Log.d(TAG, null, e);
			} catch (InterruptedException e) {
				Log.d(TAG, null, e);
			}
			Log.d(TAG, "query for bundles done.");
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // create a new executor
        _executor = Executors.newSingleThreadExecutor();
        
        // create a new DTN client
        _client = new LocalDTNClient();
        
        // register to RECEIVE intent
		IntentFilter receive_filter = new IntentFilter(de.tubs.ibr.dtn.Intent.RECEIVE);
		receive_filter.addCategory(getApplicationInfo().packageName);
        registerReceiver(_receiver, receive_filter);
        
        // create a new registration
        Registration reg = new Registration("demo02");
        
        // set the data handler for incoming bundles
        _client.setDataHandler(_handler);
        
		try {
			_client.initialize(this, reg);
		} catch (ServiceNotAvailableException e) {
			showInstallServiceDialog();
		}
        
		Log.d(TAG, "activity created");
    }
    
	private void showInstallServiceDialog() {
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        switch (which){
		        case DialogInterface.BUTTON_POSITIVE:
					final Intent marketIntent = new Intent(Intent.ACTION_VIEW);
					marketIntent.setData(Uri.parse("market://details?id=de.tubs.ibr.dtn"));
					startActivity(marketIntent);
		            break;

		        case DialogInterface.BUTTON_NEGATIVE:
		            break;
		        }
		        finish();
		    }
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getResources().getString(R.string.alert_missing_daemon));
		builder.setPositiveButton(getResources().getString(R.string.alert_yes), dialogClickListener);
		builder.setNegativeButton(getResources().getString(R.string.alert_no), dialogClickListener);
		builder.show();
	}
    
	@Override
	protected void onDestroy() {
		// unregister intent receiver
		unregisterReceiver(_receiver);
		
		// unregister at the daemon
		_client.unregister();
		
		try {
			// stop executor
			_executor.shutdown();
			
			// ... and wait until all jobs are done
			if (!_executor.awaitTermination(10, TimeUnit.SECONDS)) {
				_executor.shutdownNow();
			}
			
			// destroy DTN client
			_client.terminate();
		} catch (InterruptedException e) {
			Log.e(TAG, "Interrupted on service destruction.", e);
		}
		
		// clear all variables
		_executor = null;
		_client = null;
		
		super.onDestroy();
		
		Log.d(TAG, "activity destroyed");
	}
	
	private DataHandler _handler = new DataHandler() {
		
		private de.tubs.ibr.dtn.api.BundleID bundle = null;
		private File file = null;
		private ParcelFileDescriptor fd = null;

		@Override
		public void startBundle(de.tubs.ibr.dtn.api.Bundle bundle) {
			this.bundle = new BundleID( bundle );
		}

		@Override
		public void endBundle() {
			
			final de.tubs.ibr.dtn.api.BundleID received = this.bundle;

			// run the queue and delivered process asynchronously
			_executor.execute(new Runnable() {
		        public void run() {
					try {
						_client.getSession().delivered(received);
					} catch (Exception e) {
						Log.e(TAG, "Can not mark bundle as delivered.", e);
					}
		        }
			});

			this.bundle = null;
		}

		@Override
		public void startBlock(Block block) {
			if (block.type == 1)
			{
				File cachedir = getExternalCacheDir();
				
				// create a new temporary file
				try {
					file = File.createTempFile("payload", ".dat", cachedir);
				} catch (IOException e) {
					Log.e(TAG, "Can not create temporary file.", e);
					file = null;
				}
			}
		}

		@Override
		public void endBlock() {
			if (fd != null)
			{
				// close filedescriptor
				try {
					fd.close();
					fd = null;
				} catch (IOException e) {
					Log.e(TAG, "Can not close filedescriptor.", e);
				}
			}
			
			if (file != null)
			{
				// unset the payload file
				Log.i(TAG, "File received: " + file.getAbsolutePath());
				file = null;
			}
		}

		@Override
		public void characters(String data) {
			Log.i(TAG, "Received characters: " + new String(data));
		}

		@Override
		public ParcelFileDescriptor fd() {
			// create new filedescriptor
			try {
				fd = ParcelFileDescriptor.open(file, 
						ParcelFileDescriptor.MODE_CREATE + 
						ParcelFileDescriptor.MODE_READ_WRITE);
				
				return fd;
			} catch (FileNotFoundException e) {
				Log.e(TAG, "Can not create a filedescriptor.", e);
			}
			
			return null;
		}

		@Override
		public void payload(byte[] data) {
			Log.i(TAG, "Received payload: " + new String(data));
		}

		@Override
		public void progress(long current, long length) {
			Log.i(TAG, "Payload: " + current + " of " + length + " bytes.");
		}

		@Override
		public void finished(int startId) {
		}		
	};
}