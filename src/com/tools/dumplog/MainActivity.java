package com.tools.dumplog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private final static String TAG = "DumpTool";
	
	private static final int STOPPED = 0;
	private static final int DUMPING = 1;
	private int mStatus = STOPPED;
	private int mCmdIndex = 0;
	private boolean mEnableTcpdump = false;
	private boolean mHasUsedTcpdump = false;	// Should use root privilege
	TextView mStatusTextView = null;
	TextView mLastCmdTextView = null;
	CheckBox mTcpdumpCheckBox = null;
	Process mLogcatProcess = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mStatusTextView = (TextView)findViewById(R.id.StatusTextView);
		mLastCmdTextView = (TextView)findViewById(R.id.LastCommandTextView);
		mTcpdumpCheckBox = (CheckBox)findViewById(R.id.TcpdumpCheckBox);
		
		updateStatus();

		findViewById(R.id.StartButton).setOnClickListener(
				new Button.OnClickListener() {
					@Override
					public void onClick(View v) {
						//setTitle("您的答案是：" + ((TextView) v).getText());
						if (mStatus == DUMPING) {
							Toast.makeText(v.getContext(), "Already dumping...", Toast.LENGTH_SHORT).show();
							return;
						}
						mStatus = DUMPING;
						updateStatus();
						try {
							clearLogcat();
							startDump();
							Thread.sleep(100);
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();								
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});

		
		findViewById(R.id.StopButton).setOnClickListener(
				new Button.OnClickListener() {
					@Override
					public void onClick(View v) {
						//setTitle("您的答案是：" + ((TextView) v).getText());
						if (mStatus == STOPPED) {
							Toast.makeText(v.getContext(), "Already stopped", Toast.LENGTH_SHORT).show();
							return;
						}
						mStatus = STOPPED;
						updateStatus();
						try {
							stopDump();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});
		
		mTcpdumpCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton view, boolean isChecked) {
				mEnableTcpdump = isChecked;
				if (isChecked) {
					Toast.makeText(view.getContext(), "You should have root privilege", Toast.LENGTH_SHORT).show();
				}
			}
		});
		
        try {
            copyBinary("tcpdump");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/Dump");
        if (!destDir.exists()) {
        	destDir.mkdirs();
        }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public void onBackPressed()
	{
		new AlertDialog.Builder(this).setTitle("确认退出吗？") 
	    .setPositiveButton("确定", new DialogInterface.OnClickListener() { 
	        @Override 
	        public void onClick(DialogInterface dialog, int which) { 
	        	// 点击“确认”后的操作 
	        	try {
					MainActivity.this.stopDump();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	MainActivity.this.finish(); 
	        } 
	    }) 
	    .setNegativeButton("返回", new DialogInterface.OnClickListener() { 
	        @Override 
	        public void onClick(DialogInterface dialog, int which) { 
	        	// 点击“返回”后的操作,这里不设置没有任何操作 
	        } 
	    }).show();
	}
	
	private void startDump() throws IOException {
		Log.d("DumpLog", "startDump");
		String cmd = null;
		
		File dumpLog = new File("Dump_" + new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date()) + ".log");
		cmd = "logcat -v time -f /sdcard/Dump/" + dumpLog;
		addCmdText(cmd);
		mLogcatProcess = Runtime.getRuntime().exec(cmd);
		
		if (mEnableTcpdump) {
			File dumpPcap = new File("Dump_" + new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date()) + ".pcap");
			cmd = getApplicationContext().getFilesDir().getParent() + "/tcpdump -i any -p -s 0 -w /sdcard/Dump/" + dumpPcap;
			addCmdText(cmd);
			// For root
			Runtime.getRuntime().exec(new String[] {"su", "-c", cmd});
			mHasUsedTcpdump = true;
		}
	}
	
	private void stopDump() throws IOException {
		killLogcat();
		if (mHasUsedTcpdump) {	// Only once used tcpdump root privilege, can call killTcpdump()
			killTcpdump();
		}
	}
	
	private void updateStatus()	{
		assert(mStatusTextView != null);
		if(mStatus == STOPPED) {
			mStatusTextView.setText(R.string.status_stopped);
			mStatusTextView.setTextColor(Color.RED);
		} else {
			mStatusTextView.setText(R.string.status_dumping);
			mStatusTextView.setTextColor(Color.GREEN);
		}
	}
	
	private void addCmdText(String cmd) {
		mLastCmdTextView.setText(++mCmdIndex + ". " + cmd + "\n" + mLastCmdTextView.getText());
	}
	
	private void killLogcat() {
		Log.d("DumpLog", "killLogcat");
		
		if (mLogcatProcess != null)
		{
			mLogcatProcess.destroy();
		}
		// For root
		// String cmd = "kill $(busybox pidof logcat)";
		// mLastCmdTextView.setText(cmd);
		// Runtime.getRuntime().exec(new String[] {"su", "-c", cmd});
	}
	
	private void killTcpdump() throws IOException {
		Log.d("DumpLog", "killLogcat");
		
		// For root
		String cmd = "kill $(busybox pidof tcpdump)";
		addCmdText(cmd);
		Runtime.getRuntime().exec(new String[] {"su", "-c", cmd});
		mHasUsedTcpdump = false;
	}
	
	private void clearLogcat() throws IOException {
		Log.d("DumpLog", "clearLogcat");
		String cmd = "logcat -c";
		addCmdText(cmd);
		Process p = Runtime.getRuntime().exec(cmd);
		p.destroy();
	}
	
	private void copyBinary(String filename) throws IOException {
		String base = getApplicationContext().getFilesDir().getParent();
		String outFileName = base + "/" + filename;
		File outFile = new File(outFileName);
		if (!new File(outFileName).exists()) {
			Log.d(TAG, "Extracting " + filename + " to " + outFileName);
			InputStream is = this.getAssets().open(filename);
			byte buf[] = new byte[1024];
			int len;
			OutputStream out = new FileOutputStream(outFile);
			while ((len = is.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			out.close();
			is.close();
			String[] cmd = { "/system/bin/chmod", "0755", outFileName };
			Runtime.getRuntime().exec(cmd);
		}
	}
}
