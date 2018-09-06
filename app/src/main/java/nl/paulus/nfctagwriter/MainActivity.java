package nl.paulus.nfctagwriter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity to read/write NFC tags with own Mime-Type and Data
 * Based on the excellent tutorial by Jesse Chen
 * http://www.jessechen.net/blog/how-to-nfc-on-the-android-platform/
 */
public class MainActivity extends Activity
{
	private static final String TAG = MainActivity.class.getName();
	
	private static final int DISABLE_MODE = 0;
	private static final int READ_MODE = 1;
	private static final int WRITE_MODE = 2;
	int mMode = DISABLE_MODE;
	
	private NfcAdapter mNfcAdapter;
	private PendingIntent mNfcPendingIntent;
	
	private TextView mMimeText;
	private TextView mDataText;
	private EditText mMimeEdit;
	private EditText mDataEdit;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null)
		{
			Toast.makeText(MainActivity.this, "NFCAdapter not found",
						   Toast.LENGTH_SHORT)
				 .show();
			return;
		}
		
		mNfcPendingIntent = PendingIntent.getActivity(this,
													  0,
													  new Intent(this, MainActivity.class)
															  .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
													  0);
		
		mMimeText = findViewById(R.id.mime_read);
		mDataText = findViewById(R.id.data_read);
		mMimeEdit = findViewById(R.id.mime_edit);
		mDataEdit = findViewById(R.id.data_edit);
		
		findViewById(R.id.read_button).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				enableTagDiscovery(READ_MODE);
				
				new AlertDialog.Builder(MainActivity.this)
						.setTitle("Touch tag to read")
						.setOnCancelListener(new DialogInterface.OnCancelListener()
						{
							@Override
							public void onCancel(DialogInterface dialog)
							{
								disableTagDiscovery();
							}
						}).create()
						.show();
			}
		});
		
		findViewById(R.id.write_button).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (mMimeEdit.getText().length() <= 0)
				{
					Toast.makeText(MainActivity.this, "Mime-type cannot be empty",
								   Toast.LENGTH_SHORT)
						 .show();
					return;
				}
				
				enableTagDiscovery(WRITE_MODE);
				
				new AlertDialog.Builder(MainActivity.this)
						.setTitle("Touch tag to write")
						.setOnCancelListener(new DialogInterface.OnCancelListener()
						{
							@Override
							public void onCancel(DialogInterface dialog)
							{
								disableTagDiscovery();
							}
						}).create()
						.show();
			}
		});
		
		findViewById(R.id.copy_button).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				mMimeEdit.setText(mMimeText.getText());
				mDataEdit.setText(mDataText.getText());
			}
		});
	}
	
	private void enableTagDiscovery(int mode)
	{
		mMode = mode;
		
		IntentFilter nDefDiscovered = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
		mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent,
											 new IntentFilter[] { nDefDiscovered }, null);
	}
	
	private void disableTagDiscovery()
	{
		mMode = DISABLE_MODE;
		
		mNfcAdapter.disableForegroundDispatch(this);
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()))
		{
			NdefRecord record;
			
			switch(mMode)
			{
				case READ_MODE:
					Parcelable [] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
					if (rawMessages == null
						|| rawMessages.length <= 0)
					{
						Toast.makeText(this, "NFC tag is empty", Toast.LENGTH_LONG).show();
					}
					
					record = ((NdefMessage) rawMessages[0]).getRecords()[0];
					mMimeText.setText(new String(record.getType()));
					mDataText.setText(new String(record.getPayload()));
					Toast.makeText(this, "Tag read", Toast.LENGTH_LONG).show();
					break;
				case WRITE_MODE:
					Tag discoveredTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
					
					record = NdefRecord.createMime(mMimeEdit.getText().toString(),
												   mDataEdit.getText().toString().getBytes());
					NdefMessage message = new NdefMessage(new NdefRecord[] {record});
					if (writeTag(message, discoveredTag))
					{
						Toast.makeText(this, "NFC tag message writing successful",
									   Toast.LENGTH_LONG)
							 .show();
					}
					else
					{
						Toast.makeText(this, "NFC Tag writing failed",
									   Toast.LENGTH_LONG)
							 .show();
					}
					break;
				default:
					Toast.makeText(this, "Drop discovered tag, not actively listening...",
								   Toast.LENGTH_SHORT)
						 .show();
					break;
			}
		}
	}
	
	/**
	 * Writes an NdefMessage to a NFC tag
	 */
	public boolean writeTag(NdefMessage message, Tag tag)
	{
	    int size = message.toByteArray().length;
	    try
		{
	        Ndef ndef = Ndef.get(tag);
	        if (ndef != null)
	        {
	            ndef.connect();
	            if (! ndef.isWritable())
	            {
					Toast.makeText(MainActivity.this, "Tag is not writable",
								   Toast.LENGTH_SHORT)
						 .show();
	                return false;
	            }
	            if (ndef.getMaxSize() < size)
	            {
					Toast.makeText(MainActivity.this, "Tag is too small",
								   Toast.LENGTH_SHORT)
						 .show();
	                return false;
	            }
	            
	            ndef.writeNdefMessage(message);
	        }
	        else
			{
	            NdefFormatable format = NdefFormatable.get(tag);
	            if (format == null)
	            {
					Toast.makeText(MainActivity.this, "Tag format is not supported",
								   Toast.LENGTH_SHORT)
						 .show();
					return false;
	            }
				
				format.connect();
				format.format(message);
	        }
	    }
	    catch (Exception e)
		{
	        return false;
	    }
		
		return true;
	}
}
