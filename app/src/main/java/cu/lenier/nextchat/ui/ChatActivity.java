package cu.lenier.nextchat.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.MessageAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;

public class ChatActivity extends AppCompatActivity {
    private static final int REQ_AUDIO = 1001;

    private RecyclerView rv;
    private EditText et;
    private Button btn;
    private MessageAdapter adapter;
    private String contact;

    private MediaRecorder recorder;
    private String audioPath;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        setContentView(R.layout.activity_chat);

        rv = findViewById(R.id.rvMessages);
        et = findViewById(R.id.etMessage);
        btn= findViewById(R.id.btnSend);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        rv.setAdapter(adapter);

        contact = getIntent().getStringExtra("contact");
        String me = getSharedPreferences("prefs",MODE_PRIVATE).getString("email","");

        Executors.newSingleThreadExecutor().execute(()->
                AppDatabase.getInstance(this)
                        .messageDao()
                        .markAsRead(contact, me)
        );

        AppDatabase.getInstance(this)
                .messageDao()
                .getByContact(contact)
                .observe(this,(Observer<List<Message>>)msgs->{
                    adapter.setMessages(msgs);
                    rv.scrollToPosition(adapter.getItemCount()-1);
                });

        btn.setOnClickListener(v->{
            String t=et.getText().toString().trim();
            if(t.isEmpty())return;
            et.setText("");
            MailHelper.sendEmail(this,contact,t);
        });

        btn.setOnLongClickListener(v->{
            if(checkAudio()) startRecording();
            return true;
        });

        btn.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_UP && recorder!=null)
                stopRecordingAndSend();
            return false;
        });
    }

    private boolean checkAudio(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)
                !=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);
            return false;
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(int rq,String[]p,int[]r){
        if(rq==REQ_AUDIO && r.length>0 && r[0]==PackageManager.PERMISSION_GRANTED)
            startRecording();
        else Toast.makeText(this,"Permiso denegado",Toast.LENGTH_SHORT).show();
    }

    private void startRecording(){
        try {
            File dir=new File(getExternalFilesDir(null),"audios_enviados");
            if(!dir.exists())dir.mkdirs();
            audioPath=new File(dir,"audio_"+System.currentTimeMillis()+".3gp")
                    .getAbsolutePath();
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setOutputFile(audioPath);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.prepare();
            recorder.start();
            Toast.makeText(this,"Grabando...",Toast.LENGTH_SHORT).show();
        } catch(Exception e){
            e.printStackTrace();
            Toast.makeText(this,"Error grabar",Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordingAndSend(){
        try {
            recorder.stop();
            recorder.release();
            recorder=null;
            Toast.makeText(this,"Enviando audio...",Toast.LENGTH_SHORT).show();
            MailHelper.sendAudioEmail(this,contact,audioPath);
        } catch(Exception e){
            e.printStackTrace();
            Toast.makeText(this,"Error enviar audio",Toast.LENGTH_SHORT).show();
        }
    }
}
