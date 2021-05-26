package com.ag.ace.readwrite;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import com.ag.ace.util.BleUtil;
import com.ag.ace.util.BleUuid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Debug;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class DeviceActivity extends Activity {
	private final int PORT = 0;
	private final int LAND = 1;
	private int NUM_CHANNELS = 6;
	private int NUM_MEASURES = 3;
	private static final String TAG = "BLEDevice";
	private static final String RESCUE_NUMBER = <YOUR PHONE NUMBER HERE>;

	public static final String EXTRA_BLUETOOTH_DEVICE = "BT_DEVICE";
	private BluetoothAdapter mBTAdapter;
	private BluetoothDevice mDevice;
	private BluetoothGatt mConnGatt;
	private BluetoothGattCharacteristic mGattAlarmChar;
	private int mStatus;
	private int mCounter;
	private int[] mValues;
	private Location mLastKnownLocation;
	private boolean mCountDownStarted;
	private boolean mAlertSent;
	private AlertCountDownTimer mAlertCountDown;

	private boolean mInited = false;
	private Timer mTimer;
	private DrawSurfaceView drawSurfaceView = new DrawSurfaceView();

	private final String SENT = "SMS_SENT";
	private final String DELIVERED = "SMS_DELIVERED";

	private PendingIntent mSentIntent;
	private PendingIntent mDeliveredIntent;

	private SentSMSBroadcastReceiver mSentSMSRcvr = new SentSMSBroadcastReceiver();
	private DeliveredSMSBroadcastReceiver mDeliveredSMSRcvr = new DeliveredSMSBroadcastReceiver();

	class SentSMSBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			switch (getResultCode()) {
				case Activity.RESULT_OK:
					Toast.makeText(DeviceActivity.this, "SMS sent",
							Toast.LENGTH_SHORT).show();
					drawSurfaceView.updateCountDownStatus(0, DrawSurfaceView.COUNT_STATUS_SENT);
					break;
				case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
					Toast.makeText(DeviceActivity.this, /*"Generic failure"*/"SMS Sent",
							Toast.LENGTH_SHORT).show();
					drawSurfaceView.updateCountDownStatus(0, DrawSurfaceView.COUNT_STATUS_DELIVERED);
					break;
				case SmsManager.RESULT_ERROR_NO_SERVICE:
					Toast.makeText(DeviceActivity.this, "No service",
							Toast.LENGTH_SHORT).show();
					break;
				case SmsManager.RESULT_ERROR_NULL_PDU:
					Toast.makeText(DeviceActivity.this, "Null PDU",
							Toast.LENGTH_SHORT).show();
					break;
				case SmsManager.RESULT_ERROR_RADIO_OFF:
					Toast.makeText(getBaseContext(), "Radio off",
							Toast.LENGTH_SHORT).show();
					break;
			}
		}
	}

	class DeliveredSMSBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context arg0, Intent arg1) {
			switch (getResultCode()) {
				case Activity.RESULT_OK:
					Toast.makeText(DeviceActivity.this, "SMS delivered",
							Toast.LENGTH_SHORT).show();
					drawSurfaceView.updateCountDownStatus(0, DrawSurfaceView.COUNT_STATUS_DELIVERED);
					break;
				case Activity.RESULT_CANCELED:
					Toast.makeText(DeviceActivity.this, "SMS not delivered",
							Toast.LENGTH_SHORT).show();
					break;
			}
		}
	}

	class AlertCountDownTimer extends CountDownTimer {

		DrawSurfaceView mSurfaceView;
		Runnable mAction;

		public AlertCountDownTimer(DrawSurfaceView view, Runnable action, long millisInFuture, long countDownInterval) {
			super(millisInFuture, countDownInterval);
			mSurfaceView = view;
			mAction = action;
		}

		@Override
		public void onTick(long millisUntilFinished) {

			int progress = (int) (millisUntilFinished/1000);
			mSurfaceView.updateCountDownStatus(progress, DrawSurfaceView.COUNT_STATUS_COUNTING);
		}

		@Override
		public void onFinish() {
			mAction.run();
		}
	}

	class UpdateDataTask extends TimerTask {

		private void ReadAlarmValue()
		{
			if (mGattAlarmChar == null)
				return;

			mConnGatt.readCharacteristic(mGattAlarmChar);
		}

		public void run() {
			if (mStatus == BluetoothProfile.STATE_CONNECTED) {
				ReadAlarmValue();
			}
		}
	}

	private final BluetoothGattCallback mGattcallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				mStatus = newState;
				mConnGatt.discoverServices();
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				mStatus = newState;

				mConnGatt = mDevice.connectGatt(null, false, mGattcallback, BluetoothDevice.TRANSPORT_LE);
				mStatus = BluetoothProfile.STATE_CONNECTING;

				runOnUiThread(new Runnable() {
					public void run() {
					};
				});
			}
		};

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			for (BluetoothGattService service : gatt.getServices()) {
				if ((service == null) || (service.getUuid() == null)) {
					continue;
				}

				if (BleUuid.POS_SERVICE.equalsIgnoreCase(service.getUuid().toString())) {
					List<BluetoothGattCharacteristic> chars = service.getCharacteristics();
					for (BluetoothGattCharacteristic characteristic : chars) {
						if (BleUuid.POS_VALUE.equalsIgnoreCase(characteristic.getUuid().toString())) {
							mConnGatt.setCharacteristicNotification(characteristic, true);

							List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
							descriptors.get(0).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
							mConnGatt.writeDescriptor(descriptors.get(0));
						}
					}
				}

				else if (BleUuid.ALARM_SERVICE.equalsIgnoreCase(service.getUuid().toString())) {
					List<BluetoothGattCharacteristic> chars = service.getCharacteristics();
					for (BluetoothGattCharacteristic characteristic : chars) {
						if (BleUuid.ALARM_VALUE.equalsIgnoreCase(characteristic.getUuid().toString())) {
							mGattAlarmChar = characteristic;
						}
					}
				}
			}

			runOnUiThread(new Runnable() {
				public void run() {
					setProgressBarIndeterminateVisibility(false);
				};
			});
		};

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (BleUuid.ALARM_VALUE.equalsIgnoreCase(characteristic.getUuid().toString())) {
					byte[] bValue = characteristic.getValue();
					if ((bValue[0] != 0) && (!mCountDownStarted))
					{
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								mAlertCountDown = new AlertCountDownTimer(drawSurfaceView, new Runnable() {
									@Override
									public void run() {
										mAlertCountDown.cancel();
										sendAlert();
									}
								},10000, 1000);
								mAlertCountDown.start();
								mCountDownStarted = true;
							}
						});
					}
					else
					{
						mAlertSent = false;
					}
				}
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
										 BluetoothGattCharacteristic characteristic) {
			if (BleUuid.POS_VALUE.equalsIgnoreCase(characteristic.getUuid().toString())) {
				String sValue = characteristic.getStringValue(0);
				String[] values = sValue.split(";");
				if (values.length == NUM_MEASURES + 1) {
					int offset = (values[0].equals("A")) ? 0 : 3;
					int scale = (values[0].equals("A")) ? 1000 : 1;
					mValues[offset + 0] = (int) (Float.parseFloat(values[1]) * scale);
					mValues[offset + 1] = (int) (Float.parseFloat(values[2]) * scale);
					mValues[offset + 2] = (int) (Float.parseFloat(values[3]) * scale);
				}

				runOnUiThread(new Runnable() {
					public void run() {
						setProgressBarIndeterminateVisibility(false);
						drawSurfaceView.drawPoint(mCounter, mValues);
						mCounter++;
					}
				});
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {

			runOnUiThread(new Runnable() {
				public void run() {
					setProgressBarIndeterminateVisibility(false);
				};
			});
		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_device);

		// state
		mStatus = BluetoothProfile.STATE_DISCONNECTED;

		//drawSurfaceView.setSurfaceViewPort((SurfaceView) findViewById(R.id.surfaceView), PORT);
		drawSurfaceView.setSurfaceView((SurfaceView) findViewById(R.id.surfaceView),
				(SurfaceView) findViewById(R.id.tagView),
				(SurfaceView) findViewById(R.id.rulerView),
				getBaseContext());

		requestSmsPermission();

		if (BuildConfig.DEBUG) { // don't even consider it otherwise
			if (Debug.isDebuggerConnected()) {
				Log.d("SCREEN", "Keeping screen on for debugging, detach debugger and force an onResume to turn it off.");
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			} else {
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				Log.d("SCREEN", "Keeping screen on for debugging is now deactivated.");
			}
		}

		if (!mInited) {
			init();
			mInited = true;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mConnGatt != null) {
			if ((mStatus != BluetoothProfile.STATE_DISCONNECTING)
					&& (mStatus != BluetoothProfile.STATE_DISCONNECTED)) {
				mConnGatt.disconnect();
			}
			mConnGatt.close();
			mConnGatt = null;
		}

		mInited = false;
	}

	private static final int PERMISSION_SEND_SMS = 123;

	private void requestSmsPermission() {

		// check permission is given
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			// request permission (see result in onRequestPermissionsResult() method)
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.SEND_SMS},
					PERMISSION_SEND_SMS);
		} else {
			// permission already granted run sms send
			sendAlert();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		switch ( requestCode ) {
			case PERMISSION_SEND_SMS: {
				for( int i = 0; i < permissions.length; i++ ) {
					if( grantResults[i] == PackageManager.PERMISSION_GRANTED ) {
						Log.d( "Permissions", "Permission Granted: " + permissions[i] );
					} else if( grantResults[i] == PackageManager.PERMISSION_DENIED ) {
						Log.d( "Permissions", "Permission Denied: " + permissions[i] );
					}
				}
			}
			break;
			default: {
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.device, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem item = menu.findItem(R.id.action_switch_mode);

		if (drawSurfaceView.getMode() == DrawSurfaceView.GraphModeEnum.Overlapped)
			item.setTitle(R.string.mode_single);
		else
			item.setTitle(R.string.mode_overlapping);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == android.R.id.home) {
			// ignore
			return true;
		} else if (itemId == R.id.action_switch_mode) {
			drawSurfaceView.switchMode();
			if (drawSurfaceView.getMode() == DrawSurfaceView.GraphModeEnum.Overlapped)
				item.setTitle(R.string.mode_single);
			else
				item.setTitle(R.string.mode_overlapping);

			return true;
		} else if (itemId == R.id.action_reset) {
			drawSurfaceView.resetSurfaceViewX();
			drawSurfaceView.resetCanvas();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@SuppressLint("MissingPermission")
	private void init() {
		// BLE check
		if (!BleUtil.isBLESupported(this)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}

		// BT check
		BluetoothManager manager = BleUtil.getManager(this);
		if (manager != null) {
			mBTAdapter = manager.getAdapter();
		}
		if (mBTAdapter == null) {
			Toast.makeText(this, R.string.bt_unavailable, Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}

		// check BluetoothDevice
		if (mDevice == null) {
			mDevice = getBTDeviceExtra();
			if (mDevice == null) {
				finish();
				return;
			}
		}

		// connect to Gatt
		if ((mConnGatt == null)
				&& (mStatus == BluetoothProfile.STATE_DISCONNECTED)) {
			// try to connect
			mConnGatt = mDevice.connectGatt(this, false, mGattcallback);
			mStatus = BluetoothProfile.STATE_CONNECTING;
		} else {
			if (mConnGatt != null) {
				// re-connect and re-discover Services
				mConnGatt.connect();
				mConnGatt.discoverServices();
			} else {
				Log.e(TAG, "state error");
				finish();
				return;
			}
		}

		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
					@Override
					public void onLocationChanged(Location location) {
						mLastKnownLocation = location;
						Log.i(TAG, "Loaction changed " + location.toString());
						Log.i(TAG, "Location changed " + Location.convert(mLastKnownLocation.getLatitude(), Location.FORMAT_DEGREES) + " " + Location.convert(mLastKnownLocation.getLongitude(), Location.FORMAT_DEGREES));
					}

					@Override
					public void onStatusChanged(String provider, int status, Bundle extras) {
						Log.i(TAG, "Provider status changed " + provider);
					}

					@Override
					public void onProviderEnabled(String provider) {
						Log.i(TAG, "Provider enabled " + provider);
					}

					@Override
					public void onProviderDisabled(String provider) {
						Log.i(TAG, "Provider disabled " + provider);
					}
				}
		);

		mTimer = new Timer();
		mTimer.scheduleAtFixedRate(new UpdateDataTask(), 0, 500);

		mValues = new int[NUM_CHANNELS];

		getLocation();
		setProgressBarIndeterminateVisibility(true);
	}

	private void getLocation() {
		if (ActivityCompat.checkSelfPermission(
				this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
				this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
		} else {
			LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location != null) {
				mLastKnownLocation = location;
				Log.i(TAG, "Loaction changed " + location.toString());
				Log.i(TAG, "Location changed " + Location.convert(mLastKnownLocation.getLatitude(), Location.FORMAT_DEGREES) + " " + Location.convert(mLastKnownLocation.getLongitude(), Location.FORMAT_DEGREES));
			} else {
				Toast.makeText(this, "Unable to find location.", Toast.LENGTH_SHORT).show();
			}
		}
	}
	private BluetoothDevice getBTDeviceExtra() {
		Intent intent = getIntent();
		if (intent == null) {
			return null;
		}

		Bundle extras = intent.getExtras();
		if (extras == null) {
			return null;
		}

		return extras.getParcelable(EXTRA_BLUETOOTH_DEVICE);
	}

	private void sendAlert() {

		String text = "HELP! I felt down!";
		if (mLastKnownLocation != null)
			text += " Last known position is " + Location.convert(mLastKnownLocation.getLatitude(), Location.FORMAT_DEGREES) + " " + Location.convert(mLastKnownLocation.getLongitude(), Location.FORMAT_DEGREES);

		mSentIntent = PendingIntent.getBroadcast(DeviceActivity.this, 0, new Intent(SENT), 0);
		mDeliveredIntent = PendingIntent.getBroadcast(DeviceActivity.this, 0, new Intent(DELIVERED), 0);

		registerReceiver(mDeliveredSMSRcvr, new IntentFilter(DELIVERED));
		registerReceiver(mSentSMSRcvr, new IntentFilter(SENT));

		SmsManager smsManager = SmsManager.getDefault();
		smsManager.sendTextMessage(RESCUE_NUMBER, null, text, mSentIntent, mDeliveredIntent);

		drawSurfaceView.updateCountDownStatus(0, DrawSurfaceView.COUNT_STATUS_SENT);
	}
}
