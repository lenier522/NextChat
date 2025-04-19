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
    public interface OnItemClickListener { void onClick(String c); }
    public interface OnItemLongClickListener { void onLongClick(String c); }

    private List<String> contacts = new ArrayList<>();
    private Map<String,Integer> unreadMap = Map.of();
    private OnItemClickListener clickL;
    private OnItemLongClickListener longL;

    public void setOnItemClickListener(OnItemClickListener l){ clickL=l; }
    public void setOnItemLongClickListener(OnItemLongClickListener l){ longL=l; }
    public void setContacts(List<String> c){ contacts=c!=null?c:new ArrayList<>(); notifyDataSetChanged(); }
    public void setUnreadMap(Map<String,Integer> m){ unreadMap=m; notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int i){
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_chat_contact,p,false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h,int pos){
        String c = contacts.get(pos);
        h.tvContact.setText(c);
        int cnt = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cnt = unreadMap.getOrDefault(c,0);
        }
        h.tvBadge.setVisibility(cnt>0?View.VISIBLE:View.GONE);
        h.tvBadge.setText(String.valueOf(cnt));
        h.itemView.setOnClickListener(v->clickL.onClick(c));
        h.itemView.setOnLongClickListener(v->{ longL.onLongClick(c); return true; });
    }
    @Override public int getItemCount(){ return contacts.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvContact, tvBadge;
        VH(View v){
            super(v);
            tvContact = v.findViewById(R.id.tvContact);
            tvBadge   = v.findViewById(R.id.tvBadge);
        }
    }
}
