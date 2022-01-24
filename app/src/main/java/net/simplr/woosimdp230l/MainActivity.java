package net.simplr.woosimdp230l;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements MainPresenter.View {
    private Context context;
    private EditText etMac;
    SharedPreferences sp;
    private final String sp_file = "woosimdp230lmac";
    private final String sp_mac = "macaddress";
    private String mac;

    private LinearLayoutCompat loading;
    private MainPresenter presenter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        etMac = findViewById(R.id.Mac);
        sp = context.getSharedPreferences(sp_file, Context.MODE_PRIVATE);
        mac = sp.getString(sp_mac, "");
        System.out.println("mac : " + mac);
        if (!mac.equals("")) {
            etMac.setText(mac);
        }
        loading = findViewById(R.id.loading);
        presenter = new MainPresenter(this, sp);
//        presenter.doSinglePrint("00:15:83:16:19:89", "MB01R0001", "2022-02-02", "80", "grams", "4955773-1", "02PDX12F", "SAMPLE ESSENTIAL OIL, LEMONGRASS, 2ML", "", "");
        String action = getIntent().getStringExtra("ACTION_PRINT");
        String value = getIntent().getStringExtra("TXT_TO_PRINT");
        if (value == null) {
            value = "";
        }
        Toast.makeText(getApplicationContext(), action + " " + value, Toast.LENGTH_SHORT).show();
        if (action != null) {
            if (action.equalsIgnoreCase("SinglePrint")) {
                if (value != null) {
                    System.out.println("intent TXT_TO_PRINT: " + value);
                    presenter.addSinglePrint(value);
                }
            } else {
                if (action.equalsIgnoreCase("startPrint")) {
                    presenter.clearRecord();
                } else if (action.equalsIgnoreCase("addPrintRecord")) {
                    presenter.addRecord(value);
                } else if (action.equalsIgnoreCase("doPrint")) {
                    presenter.doPrintAll();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public void showLoading() {
        loading.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideLoading() {
        loading.setVisibility(View.GONE);
    }


    @Override
    public void isConnected(boolean bool) {
        Toast.makeText(getApplicationContext(), "isConnected " + bool, Toast.LENGTH_SHORT).show();
        Intent data = new Intent();
        data.putExtra("isSuccess", bool);
        data.putExtra("message", "");
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void closeActivity(boolean bool, String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Intent data = new Intent();
        data.putExtra("isSuccess", bool);
        data.putExtra("message", message);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void showError(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onComplete() {
        finish();
    }

    public void doDisconnect(View view) {
        presenter.doPrintAll();
    }

    public void onPrintLog(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        finish();
    }

    public void doConnect(View view) {
        presenter.doConnect("00:15:83:16:19:89");
    }
}