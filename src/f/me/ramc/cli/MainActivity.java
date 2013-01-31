package f.me.ramc.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

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

import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setupField(R.id.editText1, "f.me.ramc.cli.personName");
		setupField(R.id.editText2, "f.me.ramc.cli.phoneNumber");
		setupField(R.id.editText3, "f.me.ramc.cli.carVIN");
		setupField(R.id.editText4, "f.me.ramc.cli.carPlate");
		setupField(R.id.button2,   "f.me.ramc.cli.buyDate");
		

		Button sendBtn = (Button) findViewById(R.id.button1);
		sendBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Check if networking enabled
				// Проверить поля, должен быть хотя бы номер телефона и ФИО
				// 
				// Check last location accuracy
				//  - if accuracy low check if GPS enabled & show GPS settings
				sendDataToRAMC(); // TODO: в отдельном потоке
			}
		});

		EditText phone = (EditText) findViewById(R.id.editText2);
		if (phone.getText().length() == 0) {
			TelephonyManager tMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			phone.setText(tMgr.getLine1Number());
		}
	}
	
	private void setupField(final int editId, final String prefId) {
		final TextView field = (TextView) findViewById(editId);
		final SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);

		String val = sp.getString(prefId, "");
		field.setText(val);

		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable e) {
				sp.edit().putString(prefId, e.toString()).commit();

			}
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
	}


	private void sendDataToRAMC() {
		try {
			JSONObject data = collectCaseData();
			
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
		} catch (Exception e) {
			apologize();
		}
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
		Location loc = getLocation();
	
		if (loc != null) {
			data.put("lon", loc.getLongitude());
			data.put("lat", loc.getLatitude());
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

	
	private Location getLocation() {
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setAltitudeRequired(false);
		criteria.setBearingRequired(false);

		LocationManager locMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		String provider = locMan.getBestProvider(criteria, true);
		return locMan.getLastKnownLocation(provider);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
}
