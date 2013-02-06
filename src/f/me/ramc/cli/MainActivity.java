package f.me.ramc.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONTokener;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
	final int DATE_PICKER_DIALOG = 0;

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
		setupField(R.id.button2,   "f.me.ramc.cli.car_buyDate",    "Выбрать");
		setupField(R.id.textView3, "f.me.ramc.cli.lastCase",       "Последняя заявка: нет");
		

		((Button) findViewById(R.id.button1)).setOnClickListener(new DoSendData());

		
		Button dateBtn = (Button) findViewById(R.id.button2);
		dateBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				MainActivity.this.showDialog(DATE_PICKER_DIALOG);
			}
		});

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
			public void afterTextChanged(Editable e) {
				sp.edit().putString(prefId, e.toString()).commit();
			}
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
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
				sendDataToRAMC(data);
			} catch (Exception e) {
				apologize();
			}
		}
	}

	
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DATE_PICKER_DIALOG:
			Calendar c = Calendar.getInstance();
			return new DatePickerDialog(
					this,
					new DatePickerDialog.OnDateSetListener() {
						@Override
					    public void onDateSet(DatePicker v, int y, int m, int d) {
					    	String date = String.format("%04d-%02d-%02d", y, m+1, d);
					    	((TextView) findViewById(R.id.button2)).setText(date);
					    }
				    },
					c.get(Calendar.YEAR),
					c.get(Calendar.MONTH),
					c.get(Calendar.DAY_OF_MONTH));
		default:
			return super.onCreateDialog(id);
		}
	}

	private Location lastLocation = null;
	
    public class MyLocationListener implements LocationListener {
        public void onStatusChanged(String provider, int status, Bundle extras) {}
		public void onProviderDisabled(String provider) {}
		public void onProviderEnabled(String provider) {}

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

    
	private void sendDataToRAMC(JSONObject data) 
			throws UnsupportedEncodingException, IOException, JSONException {
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, "UTF-8");
		
		DefaultHttpClient httpclient = new DefaultHttpClient(params);
	    HttpPost post = new HttpPost("http://asgru.dyndns.org:40443/geo/case/");
	    String str = data.toString();
	    post.setEntity(new StringEntity(str, HTTP.UTF_8));
	    post.setHeader("Accept", "application/json");
	    post.setHeader("Content-type", "application/json");
	    HttpResponse resp = httpclient.execute(post);
	    JSONObject jsonResp = parseResponse(resp);
	    
	    String msg = "Создана заявка " + jsonResp.getInt("caseId");
	    msg += "\nОжидайте звонка.";
    	Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
    	((TextView) findViewById(R.id.textView3)).setText(
    			"Последняя заявка: " + jsonResp.getInt("caseId"));
	}
	
	
	private JSONObject parseResponse(HttpResponse resp)
			throws UnsupportedEncodingException, IOException, JSONException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						resp.getEntity().getContent(),
						"UTF-8"));
		String json = reader.readLine();
		JSONTokener tokener = new JSONTokener(json);
		return new JSONObject(tokener);
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
		data.put("car_buyDate",    sp.getString("f.me.ramc.cli.car_buyDate",    ""));
		return data;
	}
	
	
	private void apologize() {
		String msg = "Не удалось отправить запрос, попробуйте самостоятельно позвонить в РАМК.";
    	Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
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
            public void onClick(DialogInterface dialog,int which) {
              Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
              MainActivity.this.startActivity(intent);
            }
        });
        alertDialog.setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            	dialog.cancel();
            }
        });
        alertDialog.show();
      }

    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
}
