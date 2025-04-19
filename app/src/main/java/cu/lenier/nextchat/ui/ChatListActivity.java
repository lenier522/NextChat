package cu.lenier.nextchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.ChatListAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.UnreadCount;

public class ChatListActivity extends AppCompatActivity {
    private ChatListAdapter adapter;
    private Map<String,Integer> unreadMap = new HashMap<>();

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        setContentView(R.layout.activity_chat_list);

        RecyclerView rv = findViewById(R.id.rvChats);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatListAdapter();
        adapter.setOnItemClickListener(this::openChat);
        adapter.setOnItemLongClickListener(this::confirmDelete);
        rv.setAdapter(adapter);

        AppDatabase.getInstance(this)
                .messageDao()
                .getContacts()
                .observe(this,(Observer<List<String>>)adapter::setContacts);

        AppDatabase.getInstance(this)
                .messageDao()
                .getUnreadCounts()
                .observe(this,(Observer<List<UnreadCount>>)list->{
                    unreadMap.clear();
                    for(UnreadCount uc:list) unreadMap.put(uc.contact,uc.unread);
                    adapter.setUnreadMap(unreadMap);
                });

        FloatingActionButton fab = findViewById(R.id.fabNewChat);
        fab.setOnClickListener(v->{
            EditText input = new EditText(this);
            input.setHint("usuario@nauta.cu");
            new AlertDialog.Builder(this)
                    .setTitle("Nuevo chat")
                    .setView(input)
                    .setPositiveButton("Chatear",(d,w)->{
                        String em=input.getText().toString().trim();
                        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(em).matches()){
                            Toast.makeText(this,"Email inválido",Toast.LENGTH_SHORT).show();
                        } else {
                            startActivity(new Intent(this,ChatActivity.class)
                                    .putExtra("contact",em));
                        }
                    }).setNegativeButton("Cancelar",null).show();
        });
    }

    private void openChat(String contact) {
        String me = getSharedPreferences("prefs",MODE_PRIVATE).getString("email","");
        Executors.newSingleThreadExecutor().execute(() ->
                AppDatabase.getInstance(this)
                        .messageDao()
                        .markAsRead(contact, me)
        );
        startActivity(new Intent(this,ChatActivity.class)
                .putExtra("contact", contact));
    }

    private void confirmDelete(String contact) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar chat")
                .setMessage("¿Eliminar conversación con "+contact+"?")
                .setPositiveButton("OK",(d,w)->{
                    Executors.newSingleThreadExecutor().execute(() ->
                            AppDatabase.getInstance(this)
                                    .messageDao()
                                    .deleteByContact(contact)
                    );
                    Toast.makeText(this,"Chat eliminado",Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Cancelar",null).show();
    }
}
