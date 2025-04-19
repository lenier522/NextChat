// src/main/java/cu/lenier/nextchat/ui/ChatActivity.java
package cu.lenier.nextchat.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.concurrent.Executors;
import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.MessageAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView rvMessages;
    private EditText    etMsg;
    private Button      btnSend;
    private MessageAdapter adapter;
    private String      contact;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_chat);

        rvMessages = findViewById(R.id.rvMessages);
        etMsg      = findViewById(R.id.etMessage);
        btnSend    = findViewById(R.id.btnSend);

        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        rvMessages.setAdapter(adapter);

        contact = getIntent().getStringExtra("contact");
        String me = getSharedPreferences("prefs", MODE_PRIVATE).getString("email", "");

        Executors.newSingleThreadExecutor().execute(() ->
                AppDatabase.getInstance(this).messageDao().markAsRead(contact, me)
        );

        AppDatabase.getInstance(this)
                .messageDao()
                .getByContact(contact)
                .observe(this, (Observer<List<Message>>) messages -> {
                    adapter.setMessages(messages);
                    rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                });

        btnSend.setOnClickListener(v -> {
            String text = etMsg.getText().toString().trim();
            if (text.isEmpty()) return;
            etMsg.setText("");
            MailHelper.sendEmail(this, contact, text);
        });
    }
}
