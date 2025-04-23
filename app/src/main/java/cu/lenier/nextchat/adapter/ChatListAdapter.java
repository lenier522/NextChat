// src/main/java/cu/lenier/nextchat/adapter/ChatListAdapter.java
package cu.lenier.nextchat.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.config.AppConfig;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.VH> {
    public interface OnItemClickListener { void onClick(String contact); }
    public interface OnItemLongClickListener { void onLong(String contact); }

    private List<String> contacts;
    private Map<String,Integer> unreadMap;
    private Map<String,String> previewMap;
    private Map<String,Long> timeMap;
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

    public void setContacts(List<String> c)              { contacts = c; notifyDataSetChanged(); }
    public void setUnreadMap(Map<String,Integer> m)      { unreadMap = m; notifyDataSetChanged(); }
    public void setPreviewMap(Map<String,String> m)      { previewMap = m; notifyDataSetChanged(); }
    public void setTimeMap(Map<String,Long> m)           { timeMap = m; notifyDataSetChanged(); }
    public void setOnItemClickListener(OnItemClickListener l)         { clickListener = l; }
    public void setOnItemLongClickListener(OnItemLongClickListener l) { longClickListener = l; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_contact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(contacts.get(position));
    }

    @Override public int getItemCount() {
        return contacts == null ? 0 : contacts.size();
    }

    class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivVerified;
        TextView tvName, tvPreview, tvTime, tvBadge;

        VH(View v) {
            super(v);
            ivAvatar   = v.findViewById(R.id.ivAvatar);
            ivVerified = v.findViewById(R.id.ivVerified);
            tvName     = v.findViewById(R.id.tvName);
            tvPreview  = v.findViewById(R.id.tvPreview);
            tvTime     = v.findViewById(R.id.tvTime);
            tvBadge    = v.findViewById(R.id.tvBadge);

            v.setOnClickListener(view -> {
                if (clickListener != null)
                    clickListener.onClick(contacts.get(getAdapterPosition()));
            });
            v.setOnLongClickListener(view -> {
                if (longClickListener != null) {
                    longClickListener.onLong(contacts.get(getAdapterPosition()));
                    return true;
                }
                return false;
            });
        }

        void bind(String contact) {
            Context ctx = itemView.getContext();

            // 1) Nombre (alias si está verificado)
            String displayName = AppConfig.getDisplayName(contact);
            tvName.setText(displayName);

            // 2) Insignia de verificado
            boolean verified = AppConfig.isVerified(contact);
            ivVerified.setVisibility(verified ? View.VISIBLE : View.GONE);

            // 3) Vista previa del último mensaje
            String prev = (previewMap != null && previewMap.containsKey(contact))
                    ? previewMap.get(contact) : "";
            tvPreview.setText(prev);

            // 4) Hora formateada
            if (timeMap != null && timeMap.containsKey(contact)) {
                long ts = timeMap.get(contact);
                tvTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                        .format(new Date(ts)));
            } else {
                tvTime.setText("");
            }

            // 5) Contador de no leídos
            int count = (unreadMap != null && unreadMap.containsKey(contact))
                    ? unreadMap.get(contact) : 0;
            if (count > 0) {
                tvBadge.setVisibility(View.VISIBLE);
                tvBadge.setText(String.valueOf(count));
            } else {
                tvBadge.setVisibility(View.GONE);
            }
        }
    }
}
