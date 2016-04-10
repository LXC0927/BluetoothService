package com.example.bluetoothservice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import android.R.integer;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private TextView tvName;
	private ListView chatContent;
	private EditText etInput;
	private Button send;
	private Button refresh;

	public static final UUID MY_UUID = UUID
			.fromString("73091237-cfef-4777-826d-42336317b7cd");
	public static final String NAME = "Service";
	public static final int SEND = 1;
	public static final int RECEIVE = 2;
	private String selfDeviceName;

	private Handler mHandler;
	private AcceptThread acceptThread;
	private ConnectedThread connectedThread;

	private BluetoothAdapter mBluetoothAdapter;
	private Set<BluetoothDevice> bondedDevices;
	private BluetoothDevice bondedDevice;
	private ArrayList<String> chatData;
	private ArrayAdapter<String> adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		tvName = (TextView) findViewById(R.id.tv_name);
		chatContent = (ListView) findViewById(R.id.lv_content);
		etInput = (EditText) findViewById(R.id.et_input);
		send = (Button) findViewById(R.id.btn_send);
		refresh = (Button) findViewById(R.id.btn_refresh);
		refresh.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				getDevice();
				
			}
		});
		chatData = new ArrayList<>();
		adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
				chatData);
		chatContent.setAdapter(adapter);
		initEvents();
	}

	private void initEvents() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (!mBluetoothAdapter.isEnabled()) {
			Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(turnOn, 0);
			Toast.makeText(this, "Turned on BlueTooth", Toast.LENGTH_SHORT)
					.show();
		} else {
			Toast.makeText(this, "BlueTooth already on", Toast.LENGTH_SHORT)
					.show();
		}
		selfDeviceName = mBluetoothAdapter.getName();
		getDevice();

		mHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case SEND:
					
					break;

				case RECEIVE:
					byte[] buffer = (byte[]) msg.obj;
					String receivesString = null;
					try {
						receivesString = new String(buffer,"UTF-8");
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if (receivesString != null) {
						chatData.add(receivesString);
						adapter.notifyDataSetChanged();
					}

					break;
				}
				super.handleMessage(msg);
			}
		};
		acceptThread = new AcceptThread();
		acceptThread.start();

	}

	public void getDevice() {
		bondedDevices = mBluetoothAdapter.getBondedDevices();
		if (bondedDevices != null) {
			bondedDevice = bondedDevices.iterator().next();
			tvName.setText(bondedDevice.getName() + " "
					+ bondedDevice.getAddress());
			refresh.setVisibility(View.GONE);
		}else {
			refresh.setVisibility(View.VISIBLE);
			Toast.makeText(this, "没有配对的设备", Toast.LENGTH_SHORT).show();
		}
	}

	public void manageConnectedSocket(BluetoothSocket socket) {
		connectedThread = new ConnectedThread(socket);
		connectedThread.start();
		send.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String sendString = selfDeviceName + ":"
						+ etInput.getText().toString();
				chatData.add(sendString);
				adapter.notifyDataSetChanged();
				byte[] buffer = sendString.getBytes();
				connectedThread.write(buffer);
				etInput.setText("");
			}
		});
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			byte[] buffer = new byte[1024]; // buffer store for the stream
			int bytes; // bytes returned from read()

			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);
					// Send the obtained bytes to the UI Activity
					mHandler.obtainMessage(RECEIVE, bytes, -1, buffer)
							.sendToTarget();
				} catch (IOException e) {
					break;
				}
			}
		}

		/* Call this from the main Activity to send data to the remote device */
		public void write(byte[] bytes) {
			try {
				mmOutStream.write(bytes);
			} catch (IOException e) {
			}
		}

		/* Call this from the main Activity to shutdown the connection */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private class AcceptThread extends Thread {
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			// Use a temporary object that is later assigned to mmServerSocket,
			// because mmServerSocket is final
			BluetoothServerSocket tmp = null;
			try {
				// MY_UUID is the app's UUID string, also used by the client
				// code
				tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
						NAME, MY_UUID);
			} catch (IOException e) {
			}
			mmServerSocket = tmp;
		}

		public void run() {
			BluetoothSocket socket = null;
			// Keep listening until exception occurs or a socket is returned
			while (true) {
				try {
					socket = mmServerSocket.accept();
				} catch (IOException e) {
					break;
				}
				// If a connection was accepted
				if (socket != null) {
					// Do work to manage the connection (in a separate thread)
					Log.i("accept", "success");
					manageConnectedSocket(socket);
					try {
						mmServerSocket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel() {
			try {
				mmServerSocket.close();
			} catch (IOException e) {
			}
		}
	}

}
