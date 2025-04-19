// src/main/java/cu/lenier/nextchat/ui/ChatListActivity.java
package cu.lenier.nextchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.ChatListAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.UnreadCount;

public class ChatListActivity extends AppCompatActivity {
    RecyclerView rvChats;
    ChatListAdapter adapter;
    Map<String,Integer> unreadMap = new HashMap<>();

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_chat_list);

        rvChats = findViewById(R.id.rvChats);
        rvChats.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ChatListAdapter();
        adapter.setOnItemClickListener(this::openChat);
        adapter.setOnItemLongClickListener(this::confirmDeleteChat);
        rvChats.setAdapter(adapter);

        observeContacts();
        observeUnreadCounts();
    }

    private void observeContacts() {
        AppDatabase.getInstance(this)
                .messageDao()
                .getContacts()
                .observe(this, (Observer<List<String>>) contacts -> {
                    adapter.setContacts(contacts);
                });
    }

    private void observeUnreadCounts() {
        String me = getSharedPreferences("prefs", MODE_PRIVATE).getString("email", "");
        AppDatabase.getInstance(this)
                .messageDao()
                .getUnreadCounts()
                .observe(this, (Observer<List<UnreadCount>>) list -> {
                    unreadMap.clear();
                    for (UnreadCount uc : list) {
                        if (adapter.getContacts().contains(uc.contact)) {
                            unreadMap.put(uc.contact, uc.unread);
                        }
                    }
                    adapter.setUnreadMap(unreadMap);
                });
    }

    private void openChat(String contact) {
        String me = getSharedPreferences("prefs", MODE_PRIVATE).getString("email","");
        Executors.newSingleThreadExecutor().execute(() ->
                AppDatabase.getInstance(this).messageDao().markAsRead(contact, me)
        );
        startActivity(new Intent(this, ChatActivity.class)
                .putExtra("contact", contact));
    }

    private void confirmDeleteChat(String contact) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar chat")
                .setMessage("¿Eliminar toda la conversación con " + contact + "?")
                .setPositiveButton("OK", (d, w) -> deleteChat(contact))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteChat(String contact) {
        Executors.newSingleThreadExecutor().execute(() ->
                AppDatabase.getInstance(this).messageDao().deleteByContact(contact)
        );
        Toast.makeText(this, "Chat con " + contact + " eliminado", Toast.LENGTH_SHORT).show();
    }

    public void onNewChat(View v) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint("ejemplo@nauta.cu");

        new AlertDialog.Builder(this)
                .setTitle("Nuevo chat")
                .setView(input)
                .setPositiveButton("Chatear", (d,w)-> {
                    String email = input.getText().toString().trim();
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show();
                    } else {
                        startActivity(new Intent(this, ChatActivity.class)
                                .putExtra("contact", email));
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
