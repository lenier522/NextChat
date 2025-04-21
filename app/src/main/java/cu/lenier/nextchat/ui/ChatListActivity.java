package cu.lenier.nextchat.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.ChatListAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.model.UnreadCount;

public class ChatListActivity extends AppCompatActivity {
    private ChatListAdapter adapter;
    private Map<String,Integer> unreadMap = new HashMap<>();
    private Toolbar toolbar;
    private ConnectivityManager.NetworkCallback netCallback;
    private ConnectivityManager cm;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        // 1) Toolbar inicial
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Esperando conexión...");

        // 2) RecyclerView + Adapter
        RecyclerView rv = findViewById(R.id.rvChats);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatListAdapter();
        adapter.setOnItemClickListener(this::openChat);
        adapter.setOnItemLongClickListener(this::confirmDelete);
        rv.setAdapter(adapter);

        // 3) FAB para nuevo chat
        FloatingActionButton fab = findViewById(R.id.fabNewChat);
        fab.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("usuario@nauta.cu");
            new AlertDialog.Builder(this)
                    .setTitle("Nuevo chat")
                    .setView(input)
                    .setPositiveButton("Chatear", (d, w) -> {
                        String em = input.getText().toString().trim();
                        if (!Patterns.EMAIL_ADDRESS.matcher(em).matches()) {
                            Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show();
                        } else {
                            startActivity(new Intent(this, ChatActivity.class)
                                    .putExtra("contact", em));
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        // 4) Observers de Room
        AppDatabase db = AppDatabase.getInstance(this);
        db.messageDao().getContacts().observe(this, contacts -> {
            adapter.setContacts(contacts);
            toolbar.setTitle(getString(R.string.app_name));
            // Para cada contacto, cargar último mensaje
            Executors.newSingleThreadExecutor().execute(() -> {
                Map<String,String> previews = new HashMap<>();
                Map<String,Long>   times    = new HashMap<>();
                for (String c : contacts) {
                    Message last = db.messageDao().getLastMessageSync(c);
                    if (last != null) {
                        previews.put(
                                c,
                                last.type.equals("text")
                                        ? last.body
                                        : "Audio"
                        );
                        times.put(c, last.timestamp);
                    }
                }
                runOnUiThread(() -> {
                    adapter.setPreviewMap(previews);
                    adapter.setTimeMap(times);
                });
            });
        });


        AppDatabase.getInstance(this)
                .messageDao()
                .getUnreadCounts()
                .observe(this, (Observer<List<UnreadCount>>) list -> {
                    unreadMap.clear();
                    for (UnreadCount uc : list) {
                        unreadMap.put(uc.contact, uc.unread);
                    }
                    adapter.setUnreadMap(unreadMap);
                });

        // 5) NetworkCallback para estados de toolbar
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                handler.post(() -> {
                    toolbar.setTitle("Conectando...");
                    Executors.newSingleThreadExecutor().execute(() -> {
                        boolean ok = hasInternetAccess();
                        handler.post(() -> {
                            if (ok) {
                                toolbar.setTitle("Actualizando...");
                                // Tras 2s volvemos al nombre de la app
                                handler.postDelayed(() ->
                                        toolbar.setTitle(getString(R.string.app_name)), 2000);
                            } else {
                                // Sin internet aún
                                toolbar.setTitle("Conectando...");
                            }
                        });
                    });
                });
            }
            @Override
            public void onLost(@NonNull Network network) {
                handler.post(() -> toolbar.setTitle("Esperando conexión..."));
            }
        };
        cm.registerDefaultNetworkCallback(netCallback);
    }

    private void openChat(String contact) {
        // Marcar como leídos en background
        String me = getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("email", "");
        Executors.newSingleThreadExecutor().execute(() ->
                AppDatabase.getInstance(this)
                        .messageDao()
                        .markAsRead(contact, me)
        );

        startActivity(new Intent(this, ChatActivity.class)
                .putExtra("contact", contact));
    }

    private void confirmDelete(String contact) {
        // Mostramos diálogo de confirmación
        new AlertDialog.Builder(this)
                .setTitle("Eliminar chat")
                .setMessage("¿Eliminar toda la conversación con " + contact + "?")
                .setPositiveButton("Eliminar", (DialogInterface d, int which) -> {
                    Executors.newSingleThreadExecutor().execute(() ->
                            AppDatabase.getInstance(this)
                                    .messageDao()
                                    .deleteByContact(contact)
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Comprueba internet rápido contra un endpoint ligero */
    private boolean hasInternetAccess() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL("https://clients3.google.com/generate_204")
                            .openConnection();
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(1000);
            conn.connect();
            return conn.getResponseCode() == 204;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (cm != null && netCallback != null) {
            cm.unregisterNetworkCallback(netCallback);
        }
    }
}
