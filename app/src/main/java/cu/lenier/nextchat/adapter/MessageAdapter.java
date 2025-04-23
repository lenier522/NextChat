package cu.lenier.nextchat.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.data.MessageDao;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Message> messages;

    public void setMessages(List<Message> msgs) {
        messages = msgs;
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int pos) {
        Message m = messages.get(pos);
        if (m.sent) {
            return "audio".equals(m.type) ? 2 : 1;
        } else {
            return "audio".equals(m.type) ? 4 : 3;
        }
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        switch (viewType) {
            case 1: layout = R.layout.item_message_sent;            break;
            case 2: layout = R.layout.item_message_audio_sent;      break;
            case 3: layout = R.layout.item_message_received;        break;
            default:layout = R.layout.item_message_audio_received;  break;
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v, viewType);
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        ((VH)holder).bind(messages.get(pos));
    }

    @Override public int getItemCount() {
        return messages == null ? 0 : messages.size();
    }

    class VH extends RecyclerView.ViewHolder {
        TextView tvBody, tvTime;
        ImageView ivState;
        ImageButton btnPlay;
        int type;

        VH(View itemView, int t) {
            super(itemView);
            type = t;
            if (type == 1 || type == 3) {
                tvBody = itemView.findViewById(R.id.tvBody);
            } else {
                btnPlay = itemView.findViewById(R.id.btnPlay);
            }
            tvTime  = itemView.findViewById(R.id.tvTime);
            ivState = itemView.findViewById(R.id.ivState);

            itemView.setOnClickListener(view -> {
                Message m = messages.get(getAdapterPosition());
                MessageDao dao = AppDatabase.getInstance(view.getContext()).messageDao();
                Context ctx = view.getContext();

                if (m.sent && m.sendState == Message.STATE_FAILED) {
                    new AlertDialog.Builder(ctx)
                            .setTitle("Error al enviar")
                            .setItems(new CharSequence[]{"Reintentar","Eliminar"}, (d, which) -> {
                                if (which == 0) {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        m.sendState = Message.STATE_PENDING;
                                        dao.update(m);
                                        if ("text".equals(m.type)) MailHelper.sendEmail(ctx, m);
                                        else MailHelper.sendAudioEmail(ctx, m);
                                    });
                                } else {
                                    Executors.newSingleThreadExecutor().execute(() ->
                                            dao.deleteById(m.id)
                                    );
                                    Toast.makeText(ctx,"Mensaje eliminado",Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                } else {
                    new AlertDialog.Builder(ctx)
                            .setTitle("Eliminar mensaje")
                            .setMessage("¿Eliminar este mensaje?")
                            .setPositiveButton("Eliminar", (d, w) -> {
                                Executors.newSingleThreadExecutor().execute(() ->
                                        dao.deleteById(m.id)
                                );
                                Toast.makeText(ctx,"Mensaje eliminado",Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
            });
        }

        void bind(Message m) {
            if (type == 1 || type == 3) {
                tvBody.setText(m.body);
            } else {
                btnPlay.setOnClickListener(v -> {
                    try {
                        MediaPlayer mp = new MediaPlayer();
                        mp.setDataSource(m.attachmentPath);
                        mp.prepare();
                        mp.start();
                    } catch (Exception ignored) {}
                });
            }
            tvTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                    .format(new Date(m.timestamp)));

            if (ivState != null && m.sent) {
                int res = R.mipmap.ic_state_failed;
                if (m.sendState == Message.STATE_PENDING) res = R.mipmap.ic_state_pending;
                else if (m.sendState == Message.STATE_SENT) res = R.mipmap.ic_state_sent;
                ivState.setImageResource(res);
                ivState.setVisibility(View.VISIBLE);
            } else if (ivState != null) {
                ivState.setVisibility(View.GONE);
            }
        }
    }
}
