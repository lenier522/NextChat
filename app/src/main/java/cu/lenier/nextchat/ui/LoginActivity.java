package cu.lenier.nextchat.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Properties;

import javax.mail.Session;
import javax.mail.Store;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.service.MailService;
import cu.lenier.nextchat.util.PermissionHelper;
import cu.lenier.nextchat.work.MailSyncWorker;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPass;
    private Button   btnLogin;
    private ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String e = prefs.getString("email", "");
        String p = prefs.getString("pass", "");
        if (!e.isEmpty() && !p.isEmpty()) {
            startMailService();
            startActivity(new Intent(this, ChatListActivity.class));
            finish();
            return;
        }

        // 1) Pedir permisos al arrancar si faltan
        PermissionHelper.requestPermissionsIfNeeded(this);


        setContentView(R.layout.activity_login);
        etEmail = findViewById(R.id.etEmail);
        etPass  = findViewById(R.id.etPass);
        btnLogin= findViewById(R.id.btnLogin);
        pd = new ProgressDialog(this);
        pd.setTitle("Iniciando sesión");
        pd.setMessage("Verificando…");
        pd.setCancelable(false);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionHelper.REQ_PERMS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                // Si el usuario denegó alguno, puedes informarle:
                Toast.makeText(this,
                        "Necesitamos esos permisos para enviar audio y notificaciones",
                        Toast.LENGTH_LONG).show();
                // Y volver a pedirlos, o bien deshabilitar la función:
                PermissionHelper.requestPermissionsIfNeeded(this);
            }
        }
    }


    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPass.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) {
            showError("Complete ambos campos");
            return;
        }
        btnLogin.setEnabled(false);
        pd.show();

        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.store.protocol", "imap");
                props.put("mail.imap.host", "imap.nauta.cu");
                props.put("mail.imap.port", "143");
                props.put("mail.imap.ssl.enable", "false");
                Session sess = Session.getInstance(props);
                Store store = sess.getStore("imap");
                store.connect(email, pass);
                store.close();

                SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
                prefs.edit()
                        .putString("email", email)
                        .putString("pass", pass)
                        .apply();

                runOnUiThread(() -> {
                    pd.dismiss();
                    startMailService();
                    MailSyncWorker.schedulePeriodicSync(this);
                    startActivity(new Intent(this, ChatListActivity.class));
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    pd.dismiss();
                    btnLogin.setEnabled(true);
                    showError("Autenticación fallida");
                });
            }
        }).start();
    }

    private void startMailService() {
        Intent svc = new Intent(this, MailService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, svc);
        } else {
            startService(svc);
        }
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
}
