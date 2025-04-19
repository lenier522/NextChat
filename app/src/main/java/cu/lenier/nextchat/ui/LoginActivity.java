// src/main/java/cu/lenier/nextchat/ui/LoginActivity.java
package cu.lenier.nextchat.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button;
import android.widget.EditText;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.Store;
import cu.lenier.nextchat.R;

public class LoginActivity extends AppCompatActivity {
    EditText etEmail, etPass;
    Button btnLogin;
    ProgressDialog pd;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String e = prefs.getString("email",""), p = prefs.getString("pass","");
        if (!e.isEmpty() && !p.isEmpty()) {
            startService(new Intent(this, cu.lenier.nextchat.service.MailService.class));
            startActivity(new Intent(this, ChatListActivity.class));
            finish(); return;
        }

        setContentView(R.layout.activity_login);
        etEmail = findViewById(R.id.etEmail);
        etPass  = findViewById(R.id.etPass);
        btnLogin= findViewById(R.id.btnLogin);

        pd = new ProgressDialog(this);
        pd.setTitle("Iniciando sesión");
        pd.setMessage("Verificando credenciales...");
        pd.setCancelable(false);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPass.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) {
            showError("Complete ambos campos."); return;
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
                store.connect("imap.nauta.cu", 143, email, pass);
                store.close();

                SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
                prefs.edit().putString("email", email)
                        .putString("pass", pass)
                        .apply();

                runOnUiThread(() -> {
                    pd.dismiss();
                    startService(new Intent(this, cu.lenier.nextchat.service.MailService.class));
                    startActivity(new Intent(this, ChatListActivity.class));
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    pd.dismiss();
                    btnLogin.setEnabled(true);
                    showError("Autenticación fallida.");
                });
            }
        }).start();
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
}
