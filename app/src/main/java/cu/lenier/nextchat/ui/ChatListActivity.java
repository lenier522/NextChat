package cu.lenier.nextchat.ui;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.ChatListAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.model.UnreadCount;
import cu.lenier.nextchat.work.MailSyncWorker;

public class ChatListActivity extends AppCompatActivity {
    private ChatListAdapter adapter;
    private final Map<String, Integer> unreadMap = new HashMap<>();
    private Toolbar toolbar;
    private ConnectivityManager.NetworkCallback netCallback;
    private ConnectivityManager cm;

    // Handler para disparar sincronización periódica
    private final Handler syncHandler = new Handler();
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            MailSyncWorker.forceSyncNow(ChatListActivity.this);
            syncHandler.postDelayed(this, 5_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        // Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Esperando conexión...");

        // RecyclerView + Adapter
        RecyclerView rv = findViewById(R.id.rvChats);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatListAdapter();
        adapter.setOnItemClickListener(this::openChat);
        adapter.setOnItemLongClickListener(this::confirmDelete);
        rv.setAdapter(adapter);

        // FAB para nuevo chat
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

        AppDatabase db = AppDatabase.getInstance(this);

        // Observa la lista de contactos y actualiza orden y vistas previas
        db.messageDao().getContacts().observe(this, contacts -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                // Construye mapas de vista previa y timestamp
                Map<String, String> previews = new HashMap<>();
                Map<String, Long> times = new HashMap<>();
                for (String c : contacts) {
                    Message last = db.messageDao().getLastMessageSync(c);
                    if (last != null) {
                        previews.put(c, last.type.equals("text") ? last.body : "Audio");
                        times.put(c, last.timestamp);
                    } else {
                        previews.put(c, "");
                        times.put(c, 0L);
                    }
                }

                // Ordena contactos por último timestamp descendente
                List<String> sorted = new ArrayList<>(contacts);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Collections.sort(sorted, (a, b) ->
                            Long.compare(times.getOrDefault(b, 0L), times.getOrDefault(a, 0L))
                    );
                }

                // Vuelve al hilo UI para aplicar
                runOnUiThread(() -> {
                    toolbar.setTitle(getString(R.string.app_name));
                    adapter.setContacts(sorted);
                    adapter.setPreviewMap(previews);
                    adapter.setTimeMap(times);
                });
            });
        });

        // Observa recuento de no leídos
        db.messageDao().getUnreadCounts()
                .observe(this, list -> {
                    unreadMap.clear();
                    for (UnreadCount uc : list) {
                        unreadMap.put(uc.contact, uc.unread);
                    }
                    adapter.setUnreadMap(unreadMap);
                });

        // Network callback para estados de conexión en toolbar
        cm = getSystemService(ConnectivityManager.class);
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                syncHandler.post(() -> toolbar.setTitle("Conectando..."));
            }

            @Override
            public void onLost(@NonNull Network network) {
                syncHandler.post(() -> toolbar.setTitle("Esperando conexión..."));
            }
        };
        cm.registerDefaultNetworkCallback(netCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Inicia el loop de sincronización
        syncHandler.post(syncRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Detiene el loop
        syncHandler.removeCallbacks(syncRunnable);
    }

    private void openChat(String contact) {
        // Marca como leídos en background
        String me = getSharedPreferences("prefs", MODE_PRIVATE).getString("email", "");
        Executors.newSingleThreadExecutor().execute(() ->
                AppDatabase.getInstance(this)
                        .messageDao()
                        .markAsRead(contact, me)
        );
        // Abre ChatActivity
        startActivity(new Intent(this, ChatActivity.class)
                .putExtra("contact", contact));
    }

    private void confirmDelete(String contact) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar chat")
                .setMessage("¿Eliminar toda la conversación con " + contact + "?")
                .setPositiveButton("Eliminar", (d, w) -> Executors.newSingleThreadExecutor().execute(() ->
                        AppDatabase.getInstance(this)
                                .messageDao()
                                .deleteByContact(contact)
                ))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean hasInternetAccess() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL("https://clients3.google.com/generate_204").openConnection();
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
        // Limpia callbacks y registra network
        syncHandler.removeCallbacksAndMessages(null);
        if (cm != null && netCallback != null) {
            cm.unregisterNetworkCallback(netCallback);
        }
    }
}
