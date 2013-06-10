package f.me.ramc.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;
import org.json.JSONException;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		TelephonyManager tMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String usersPhoneNum = tMgr.getLine1Number();

		setupField(R.id.editText1, "f.me.ramc.cli.contact_name",   "");
		setupField(R.id.editText2, "f.me.ramc.cli.contact_phone1", usersPhoneNum);
		setupField(R.id.editText3, "f.me.ramc.cli.car_vin",        "");
		setupField(R.id.editText4, "f.me.ramc.cli.car_plateNum",   "");
		setupField(R.id.textView3, "f.me.ramc.cli.lastCase",       "Последняя заявка: нет");
		
		((Button) findViewById(R.id.button1)).setOnClickListener(new DoSendData());

		Button locBtn = (Button) findViewById(R.id.button3);
		locBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				LocationManager locMan = (LocationManager) getSystemService(LOCATION_SERVICE);
		        if(!locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
		        	locationServicesAlert();
		        }
		        
		        if(locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)
		        || locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
		        {
					MyLocationListener locListener = new MyLocationListener();
					if (locMan.isProviderEnabled(LocationManager.GPS_PROVIDER))
						locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locListener);
					if (locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
						locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 0, locListener);
			        
					((TextView) findViewById(R.id.button3)).setText("Подождите немного");
		        }
			}
		});
	}
	
	private void setupField(final int editId, final String prefId, String def) {
		final TextView field = (TextView) findViewById(editId);
		final SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);

		String val = sp.getString(prefId, def);
		field.setText(val);

		field.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable e) {
				sp.edit().putString(prefId, e.toString()).commit();
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
	}


	class DoSendData implements OnClickListener {
		@Override
		public void onClick(View v) {
			if (!checkNetworkConnection()) return;
			try {
				JSONObject data = collectCaseData();
				boolean validData
					= data.getString("contact_name").length() > 0
					&& data.getString("contact_phone1").length() > 0;
				if (!validData) {
					Toast.makeText(
							getBaseContext(),
							"Нужно заполнить хотя бы ФИО и номер телефона, " +
							"иначе мы никак не сможем с Вами связаться.",
							Toast.LENGTH_LONG).show();
					return;
				}
				new SendToRAMC().execute(data);
			} catch (Exception e) {
				String msg = "Не удалось отправить запрос, попробуйте самостоятельно позвонить в РАМК.";
		    	Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
			}			
		}
	}

	private Location lastLocation = null;
	
    public class MyLocationListener implements LocationListener {
        @Override
		public void onStatusChanged(String provider, int status, Bundle extras) {}
		@Override
		public void onProviderDisabled(String provider) {}
		@Override
		public void onProviderEnabled(String provider) {}

        @Override
		public void onLocationChanged(Location loc) {
            if (loc != null && loc.getAccuracy() < 1000) {
            	String locStr = String.format("%.5f;%.5f", loc.getLongitude(), loc.getLatitude());
            	((TextView) findViewById(R.id.button3)).setText(locStr);
            	lastLocation = loc;

            	LocationManager locMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);        
		        locMan.removeUpdates(this);
            }
        }
    }
	
	private class SendToRAMC extends AsyncTask<JSONObject, Void, JSONObject> {
		
		@Override
		protected JSONObject doInBackground(JSONObject... datas) {
			try {
				DefaultHttpClient httpclient = new DefaultHttpClient();
				HttpPost httpPostRequest = new HttpPost("http://portal.ruamc.ru:40443/geo/case/");
				StringEntity se;
				se = new StringEntity(datas[0].toString());
				
				// Set HTTP parameters
				httpPostRequest.setEntity(se);
				httpPostRequest.setHeader("Accept", "application/json");
				httpPostRequest.setHeader("Content-type", "application/json");
				// only set this parameter if you would like to use gzip compression
				// httpPostRequest.setHeader("Accept-Encoding", "gzip");
				
				HttpResponse response = httpclient.execute(httpPostRequest);
				
				// Get hold of the response entity (-> the data):
				HttpEntity entity = response.getEntity();
				
				if (entity != null) {
					// Read the content stream
					InputStream instream = entity.getContent();
					Header contentEncoding = response.getFirstHeader("Content-Encoding");
					if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
						instream = new GZIPInputStream(instream);
					}
	
					// convert content stream to a String
					String resultString= convertStreamToString(instream);
					instream.close();
					
					// Transform the String into a JSONObject
					JSONObject jsonObjRecv = new JSONObject(resultString);
	
					return jsonObjRecv;
				} 
	
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		
		private String convertStreamToString(InputStream is) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();
	
			String line = null;
			try {
				while ((line = reader.readLine()) != null) {
					sb.append(line + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return sb.toString();
		}
		
		@Override
		protected void onPostExecute(JSONObject resp) {
			Activity mc = MainActivity.this;
			Context bc = mc.getBaseContext();
			try {
			    String msg = "Создана заявка " + resp.getInt("caseId");
			    msg += "\nОжидайте звонка.";
		    	Toast.makeText(bc, msg, Toast.LENGTH_LONG).show();
		    	((TextView) mc.findViewById(R.id.textView3)).setText(
		    			"Последняя заявка: " + resp.getInt("caseId"));
			} catch (Exception e) {
		    	Toast.makeText(bc, R.string.case_send_fail_message, Toast.LENGTH_LONG).show();
			}
		}
		
	}
	
	
	private JSONObject collectCaseData() throws JSONException {
		final SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);

		JSONObject data = new JSONObject();
		
		if (this.lastLocation != null) {
			data.put("lon", this.lastLocation.getLongitude());
			data.put("lat", this.lastLocation.getLatitude());
		}

		data.put("contact_name",   sp.getString("f.me.ramc.cli.contact_name",   ""));
		data.put("contact_phone1", sp.getString("f.me.ramc.cli.contact_phone1", ""));
		data.put("car_vin",        sp.getString("f.me.ramc.cli.car_vin",        ""));
		data.put("car_plateNum",   sp.getString("f.me.ramc.cli.car_plateNum",   ""));
		return data;
	}
	
	
	public boolean checkNetworkConnection() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean connected = false;
        for (NetworkInfo ni : cm.getAllNetworkInfo()) {
            connected |= ni.isConnected();
        }
        
        if (!connected) {
        	AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle("Ошибка связи");
            alertDialog.setMessage(
    			"Для отправки информации в РАМК необходимо соединение с интернетом."
        		+ "\nПроверьте соединение и попробуйте ещё раз.");
            alertDialog.setPositiveButton("Хорошо", new DialogInterface.OnClickListener() {
                @Override
				public void onClick(DialogInterface dialog,int which) {}
            });
            alertDialog.show();
        }
        return connected;
	}
	
	
    public void locationServicesAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        alertDialog.setTitle("Настройки GPS");
        alertDialog.setMessage("Необходимо включить GPS. Перейти в меню с настройками?");
        alertDialog.setPositiveButton("Перейти", new DialogInterface.OnClickListener() {
            @Override
			public void onClick(DialogInterface dialog,int which) {
              Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
              MainActivity.this.startActivity(intent);
            }
        });
        alertDialog.setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
            @Override
			public void onClick(DialogInterface dialog, int which) {
            	dialog.cancel();
            }
        });
        alertDialog.show();
      }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_info:
            	Intent intent = new Intent(this, InfoActivity.class);
            	startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
}
