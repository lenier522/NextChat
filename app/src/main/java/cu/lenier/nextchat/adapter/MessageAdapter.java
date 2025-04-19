package cu.lenier.nextchat.adapter;

import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.model.Message;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int SENT_TEXT  = 1;
    private static final int RECV_TEXT  = 2;
    private static final int SENT_AUDIO = 3;
    private static final int RECV_AUDIO = 4;

    private List<Message> messages = new ArrayList<>();

    public void setMessages(List<Message> m){
        messages = m!=null?m:new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int pos){
        Message m = messages.get(pos);
        boolean isAudio = "audio".equals(m.type);
        return m.sent
                ? isAudio?SENT_AUDIO:SENT_TEXT
                : isAudio?RECV_AUDIO:RECV_TEXT;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p,int vt){
        int layout;
        switch(vt){
            case SENT_AUDIO:   layout=R.layout.item_message_audio_sent;    break;
            case RECV_AUDIO:   layout=R.layout.item_message_audio_received;break;
            case RECV_TEXT:    layout=R.layout.item_message_received;      break;
            case SENT_TEXT:
            default:           layout=R.layout.item_message_sent;          break;
        }
        View v = LayoutInflater.from(p.getContext()).inflate(layout,p,false);
        return new VH(v,vt);
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,int pos){
        ((VH)h).bind(messages.get(pos));
    }

    @Override public int getItemCount(){ return messages.size(); }

    class VH extends RecyclerView.ViewHolder {
        TextView tvBody, tvTime;
        ImageButton btnPlay;
        int type;

        VH(View v,int t){
            super(v); type=t;
            tvTime = v.findViewById(R.id.tvTime);
            if(type==SENT_TEXT||type==RECV_TEXT){
                tvBody = v.findViewById(R.id.tvBody);
            } else {
                btnPlay = v.findViewById(R.id.btnPlay);
            }
        }

        void bind(Message m){
//            tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
            tvTime.setText(new SimpleDateFormat("hh:mm a",Locale.getDefault())
                    .format(new Date(m.timestamp)));
            if(type==SENT_TEXT||type==RECV_TEXT){
                tvBody.setText(m.body);
            } else {
                btnPlay.setOnClickListener(v->{
                    MediaPlayer mp = new MediaPlayer();
                    try{
                        mp.setDataSource(m.attachmentPath);
                        mp.prepare();
                        mp.start();
                    } catch(IOException e){
                        e.printStackTrace();
                    }
                });
            }
        }
    }
}
