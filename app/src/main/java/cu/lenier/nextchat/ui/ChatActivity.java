package cu.lenier.nextchat.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.MessageAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;
import cu.lenier.nextchat.util.SimpleTextWatcher;

public class ChatActivity extends AppCompatActivity {
    private static final int REQ_AUDIO = 1001;

    private RecyclerView rv;
    private EditText     et;
    private FloatingActionButton fab;
    private MessageAdapter adapter;
    private String contact, me;

    private MediaRecorder recorder;
    private String audioPath;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        setContentView(R.layout.activity_chat);

        rv   = findViewById(R.id.rvMessages);
        et   = findViewById(R.id.etMessage);
        fab  = findViewById(R.id.fabSend);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        rv.setAdapter(adapter);

        contact = getIntent().getStringExtra("contact");
        me      = getSharedPreferences("prefs", MODE_PRIVATE).getString("email","");

        // Observamos mensajes y marcamos como leídos
        AppDatabase.getInstance(this)
                .messageDao()
                .getByContact(contact)
                .observe(this, (Observer<List<Message>>) msgs -> {
                    adapter.setMessages(msgs);
                    rv.scrollToPosition(adapter.getItemCount()-1);
                    // marcamos leídos
                    Executors.newSingleThreadExecutor().execute(() ->
                            AppDatabase.getInstance(this)
                                    .messageDao()
                                    .markAsRead(contact, me)
                    );
                });

        // Cambiar icono al escribir
        et.addTextChangedListener(new SimpleTextWatcher(){
            @Override public void onTextChanged(CharSequence s, int st, int b, int c){
                if (s.toString().trim().isEmpty()) {
                    fab.setImageResource(R.mipmap.ic_mic);
                } else {
                    fab.setImageResource(R.mipmap.ic_send);
                }
            }
        });

        // Click: enviar texto si hay
        fab.setOnClickListener(v -> {
            String txt = et.getText().toString().trim();
            if (!txt.isEmpty()) {
                et.setText("");
                MailHelper.sendEmail(this, contact, txt);
            } else {
                Toast.makeText(this, "Mantén presionado para grabar audio", Toast.LENGTH_SHORT).show();
            }
        });

        // Long press: start recording
        fab.setOnLongClickListener(v -> {
            if (checkAudioPerm()) startRecording();
            return true;
        });

        // Al soltar: stop & send audio
        fab.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP && recorder != null) {
                stopRecordingAndSend();
            }
            return false;
        });
    }

    private boolean checkAudioPerm(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_AUDIO
            );
            return false;
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g){
        if (r == REQ_AUDIO && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this,"Permiso de audio denegado",Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording(){
        try {
            File dir = new File(getExternalFilesDir(null),"audios_enviados");
            if (!dir.exists()) dir.mkdirs();
            audioPath = new File(dir,"audio_"+System.currentTimeMillis()+".3gp")
                    .getAbsolutePath();

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setOutputFile(audioPath);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.prepare(); recorder.start();

            Toast.makeText(this,"Grabando audio...",Toast.LENGTH_SHORT).show();
        } catch (Exception e){
            e.printStackTrace();
            Toast.makeText(this,"Error al grabar",Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordingAndSend(){
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            Toast.makeText(this,"Enviando audio...",Toast.LENGTH_SHORT).show();
            MailHelper.sendAudioEmail(this, contact, audioPath);
        } catch (Exception e){
            e.printStackTrace();
            Toast.makeText(this,"Error al enviar audio",Toast.LENGTH_SHORT).show();
        }
    }
}
