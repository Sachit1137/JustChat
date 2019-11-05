package com.bluetoothmessenger;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

 @TargetApi(Build.VERSION_CODES.HONEYCOMB) @SuppressLint("HandlerLeak") public class MainActivity extends Activity{
	final String TAG="MainActivity";
	final boolean D=true;
	boolean discover;
	
	final static int MESSAGE_STATE_CHANGE=1;
	final static int MESSAGE_READ=2;
	final static int MESSAGE_WRITE=3;
	final static int MESSAGE_DEVICE_NAME=4;
	final static int MESSAGE_TOAST=5;
	
	final static String DEVICE_NAME="device_name";
	final static String TOAST="toast";
	
	final static int REQUEST_CONNECT_DEVICE_SECURE=1;
	final static int REQUEST_CONNECT_DEVICE_INSECURE=2;
	final static int REQUEST_ENABLE_BT=3;
	
	ListView con_view;
	EditText outEditText;
	Button send;
	
	String ConnectedDevName=null;
	
	ArrayAdapter<String> ConvArrayAdapter;
	
	StringBuffer outStringBuffer;
	
	BluetoothAdapter ba=null;
	
	BluetoothChatService bt_chat_ser=null;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(D)
			Log.e(TAG,"onCreate()");
		
		setContentView(R.layout.main);
	    ba=BluetoothAdapter.getDefaultAdapter();
		if(ba==null){
			Toast.makeText(this,"Bluetooth Not Available",Toast.LENGTH_LONG).show();
			finish();
			return;
		}
	}
	
	protected void onStart() {
		super.onStart();
		if(D)
			Log.e(TAG, "onStart()");
		if(!ba.isEnabled()){
			Intent i=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(i, REQUEST_ENABLE_BT);
		}
		else{
				if(bt_chat_ser==null)
				setupChat();
		}
	}
	protected synchronized void onResume() {
		super.onResume();
		if(D)
			Log.e(TAG, "onResume()");
		if(bt_chat_ser!=null){
			if(bt_chat_ser.getState()==BluetoothChatService.STATE_NONE){
				bt_chat_ser.start();				
			}
		}
	}	
	private void setupChat(){
		Log.d(TAG, "setupChat()");		
		ConvArrayAdapter=new ArrayAdapter<String>(this,R.layout.message);
		con_view=(ListView)findViewById(R.id.in);
		con_view.setAdapter(ConvArrayAdapter);
		outEditText=(EditText)findViewById(R.id.edit_text_out);
		outEditText.setOnEditorActionListener(mWriteListener);
		send=(Button)findViewById(R.id.button_send);
		send.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
					TextView tv=(TextView)findViewById(R.id.edit_text_out);
					String msg=tv.getText().toString();
					sendMessage(msg);
			}
		});
		bt_chat_ser=new BluetoothChatService(this, mHandler);
		outStringBuffer=new StringBuffer("");
	}
	@Override
	public synchronized void onPause() {
		super.onPause();
		if(D)
			Log.e(TAG, "onPause()");
	}
	@Override
	protected void onStop() {
		super.onStop();
		if(D)
			Log.e(TAG, "onStop()");
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(bt_chat_ser!=null)
			bt_chat_ser.stop();
		if(D)
			Log.e(TAG, "onDestroy()");
	}
	private void ensureDiscoverable(){
		if(D)
			Log.d(TAG, "ensureDisc()");
		if(ba.getScanMode()!=BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
			Intent i=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
			startActivity(i);
		}		
	}
	private void sendMessage(String msg){
		if(bt_chat_ser.getState()!=BluetoothChatService.STATE_CONNECTED){
			Toast.makeText(this,R.string.not_connected,Toast.LENGTH_SHORT).show();
			return;
		}		
		if(msg.length()>0){
			byte[] send=msg.getBytes();
			bt_chat_ser.write(send);
			outStringBuffer.setLength(0);
			outEditText.setText(outStringBuffer);
		}
	}
	private TextView.OnEditorActionListener mWriteListener=new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			if(actionId==EditorInfo.IME_NULL && event.getAction()==KeyEvent.ACTION_UP){
				String msg=v.getText().toString();
				sendMessage(msg);
			}
			if(D)
				Log.i(TAG, "END onEditorAction()");
			return true;
		}
	};
	
	final void setStatus(int resId){
		final ActionBar ab=getActionBar();
		ab.setSubtitle(resId);
	}
	
	final void setStatus(CharSequence subTitle){
		final ActionBar ab=getActionBar();
		ab.setSubtitle(subTitle);
	}
	
       final Handler mHandler=new Handler(){
	 @Override
		public void handleMessage(Message msg){
			switch(msg.what){
			case MESSAGE_STATE_CHANGE:
				if(D)
					Log.i(TAG,"MESSAGE_STATE_CHANGE: "+msg.arg1);
				switch(msg.arg1){
					case BluetoothChatService.STATE_CONNECTED:
						setStatus(getString(R.string.title_connected_to,ConnectedDevName));
						ConvArrayAdapter.clear();
						break;
					case BluetoothChatService.STATE_CONNECTING:
						setStatus(R.string.title_connecting);
						break;
					case BluetoothChatService.STATE_LISTEN:
					case BluetoothChatService.STATE_NONE:
						setStatus(R.string.title_not_connected);
						break;
				}
				break;
				case MESSAGE_WRITE:
					byte[] writeBuf=(byte[]) msg.obj;
					String writeMsg=new String(writeBuf);
					ConvArrayAdapter.add("Me: " + writeMsg);
					break;
				case MESSAGE_READ:
					byte[] readBuf=(byte[])msg.obj;
					String readMsg=new String(readBuf,0,msg.arg1);
					ConvArrayAdapter.add(ConnectedDevName+": "+readMsg);
					break;
				case MESSAGE_DEVICE_NAME:
					ConnectedDevName=msg.getData().getString(DEVICE_NAME);
					Toast.makeText(getApplicationContext(), "Connected To "+ConnectedDevName,Toast.LENGTH_SHORT).show();
					break;
				case MESSAGE_TOAST:
					Toast.makeText(getApplicationContext(),msg.getData().getString(TOAST),Toast.LENGTH_SHORT).show();
					break;
			}
		}
	};
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(D)
			Log.d(TAG,"onActivityResult " + resultCode);
		switch(requestCode){
		case REQUEST_CONNECT_DEVICE_SECURE:
			if(resultCode==Activity.RESULT_OK){
				connectDevice(data,true);
			}
			break;
		case REQUEST_CONNECT_DEVICE_INSECURE :
			if(resultCode==Activity.RESULT_OK){
				connectDevice(data,false);
			}
			break;
		
		case REQUEST_ENABLE_BT:
				if(resultCode==Activity.RESULT_OK){
				setupChat();
				}
				else{
					Log.d(TAG, "BT not enabled");
					Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
					finish();
				}
		}
	}
	private void connectDevice(Intent data,boolean secure){
		String address=data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		BluetoothDevice device=ba.getRemoteDevice(address);
		bt_chat_ser.connect(device,secure);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {		
		getMenuInflater().inflate(R.menu.option_menu, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
		switch (item.getItemId()) {
			case R.id.secure_connect_scan:
				serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
				return true;
			//case R.id.insecure_connect_scan:
				//serverIntent = new Intent(this, DeviceListActivity.class);
				//startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
				//return true;*/
			case R.id.discoverable:
				if(discover == false){
					discover=true;
					AlertDialog.Builder ab=new AlertDialog.Builder(this);
					ab.setTitle("Discovery Mode");
					ab.setMessage("Make Your Device Visible?");
					ab.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface di, int which) {
							ensureDiscoverable();
							}
					});
					ab.setNegativeButton("No", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface di, int which) {
							di.dismiss();
						}
					});
					ab.show();					
				}
				else
					Toast.makeText(this, "Your device is visible to nearby devices...", Toast.LENGTH_LONG).show();
				return true;
		}
		return false;
	}
}