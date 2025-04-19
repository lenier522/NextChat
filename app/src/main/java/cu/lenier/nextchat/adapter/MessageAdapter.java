// src/main/java/cu/lenier/nextchat/adapter/MessageAdapter.java
package cu.lenier.nextchat.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import cu.lenier.nextchat.R;
import cu.lenier.nextchat.model.Message;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Message> messages = new ArrayList<>();
    private static final int SENT = 1, RECEIVED = 2;

    public void setMessages(List<Message> m) {
        messages = m != null ? m : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int pos) {
        return messages.get(pos).sent ? SENT : RECEIVED;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        int layout = vt == SENT
                ? R.layout.item_message_sent
                : R.layout.item_message_received;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        VH vh = (VH)h;
        Message m = messages.get(pos);
        vh.tvBody.setText(m.body);
        vh.tvTime.setText(new SimpleDateFormat("HH:mm").format(new Date(m.timestamp)));
    }

    @Override public int getItemCount() { return messages.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvBody, tvTime;
        VH(View v) {
            super(v);
            tvBody = v.findViewById(R.id.tvBody);
            tvTime = v.findViewById(R.id.tvTime);
        }
    }
}
