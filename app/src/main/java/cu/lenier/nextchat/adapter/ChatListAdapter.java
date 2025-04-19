// src/main/java/cu/lenier/nextchat/adapter/ChatListAdapter.java
package cu.lenier.nextchat.adapter;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import cu.lenier.nextchat.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.VH> {
    public interface OnItemClickListener { void onClick(String contact); }
    public interface OnItemLongClickListener { void onLongClick(String contact); }

    private List<String> contacts = new ArrayList<>();
    private Map<String,Integer> unreadMap = Map.of();
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

    public void setOnItemClickListener(OnItemClickListener l) { clickListener = l; }
    public void setOnItemLongClickListener(OnItemLongClickListener l) { longClickListener = l; }

    public void setContacts(List<String> c) {
        contacts = c != null ? c : new ArrayList<>();
        notifyDataSetChanged();
    }
    public List<String> getContacts() { return contacts; }

    public void setUnreadMap(Map<String,Integer> m) {
        unreadMap = m;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_contact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String c = contacts.get(position);
        holder.tvContact.setText(c);

        int count = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            count = unreadMap.getOrDefault(c, 0);
        }
        holder.tvBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        holder.tvBadge.setText(String.valueOf(count));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(c);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(c);
            return true;
        });
    }

    @Override public int getItemCount() { return contacts.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvContact, tvBadge;
        VH(View v) {
            super(v);
            tvContact = v.findViewById(R.id.tvContact);
            tvBadge   = v.findViewById(R.id.tvBadge);
        }
    }
}
