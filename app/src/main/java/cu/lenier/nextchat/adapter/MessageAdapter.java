package cu.lenier.nextchat.adapter;

import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import rm.com.audiowave.AudioWaveView;
import cu.lenier.nextchat.R;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.data.MessageDao;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;
import rm.com.audiowave.OnSamplingListener;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Message> messages;
    private final Handler uiHandler = new Handler();

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
        try {
            ((VH)holder).bind(messages.get(pos));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public int getItemCount() {
        return messages == null ? 0 : messages.size();
    }

    class VH extends RecyclerView.ViewHolder {
        TextView tvBody, tvTime, tvDuration;
        ImageView ivState;
        ImageButton btnPlay;
        AudioWaveView waveform;
        int type;

        private MediaPlayer mp;
        private Visualizer visualizer;

        // Runnable ahora sin forward-reference
        private final Runnable progressUpdater = new Runnable() {
            @Override public void run() {
                if (mp != null && mp.isPlaying()) {
                    int pos = mp.getCurrentPosition();
                    int dur = mp.getDuration();
                    float pct = pos * 100f / dur;
                    waveform.setProgress(pct);
                    uiHandler.postDelayed(this, 50);
                }
            }
        };

        VH(View itemView, int t) {
            super(itemView);
            type = t;

            if (type == 1 || type == 3) {
                tvBody = itemView.findViewById(R.id.tvBody);
            } else {
                btnPlay    = itemView.findViewById(R.id.btnPlay);
                waveform   = itemView.findViewById(R.id.waveform);
                tvDuration = itemView.findViewById(R.id.tvDuration);
            }
            tvTime  = itemView.findViewById(R.id.tvTime);
            ivState = itemView.findViewById(R.id.ivState);

            itemView.setOnClickListener(view -> {
                Message m = messages.get(getAdapterPosition());
                MessageDao dao = AppDatabase.getInstance(view.getContext()).messageDao();
                if (m.sent && m.sendState == Message.STATE_FAILED) {
                    new AlertDialog.Builder(view.getContext())
                            .setTitle("Error al enviar")
                            .setItems(new CharSequence[]{"Reintentar","Eliminar"}, (d, which) -> {
                                if (which == 0) {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        m.sendState = Message.STATE_PENDING;
                                        dao.update(m);
                                        if ("text".equals(m.type)) MailHelper.sendEmail(view.getContext(), m);
                                        else MailHelper.sendAudioEmail(view.getContext(), m);
                                    });
                                } else {
                                    Executors.newSingleThreadExecutor().execute(() -> dao.deleteById(m.id));
                                }
                            }).show();
                } else {
                    new AlertDialog.Builder(view.getContext())
                            .setTitle("Eliminar mensaje")
                            .setMessage("¿Eliminar este mensaje?")
                            .setPositiveButton("Eliminar", (d, w) -> {
                                Executors.newSingleThreadExecutor().execute(() -> dao.deleteById(m.id));
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
            });
        }

        void bind(Message m) throws IOException {
            // Texto normal
            if (type == 1 || type == 3) {
                tvBody.setText(m.body);
                tvTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                        .format(new Date(m.timestamp)));
            }
            // Audio
            else {
                // Hora de envío
                tvTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                        .format(new Date(m.timestamp)));

                // Duración real
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(itemView.getContext(), Uri.fromFile(new File(m.attachmentPath)));
                String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                mmr.release();
                int durMs = Integer.parseInt(durStr);
                tvDuration.setText(new SimpleDateFormat("mm:ss", Locale.getDefault())
                        .format(new Date(durMs)));

                waveform.setProgress(0f);
                btnPlay.setOnClickListener(v -> {
                    if (mp != null && mp.isPlaying()) {
                        stopPlayback();
                    } else {
                        startPlayback(m);
                    }
                });
            }

            // Icono de estado (solo para enviados)
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

        private void startPlayback(Message m) {
            try {
                mp = new MediaPlayer();
                mp.setDataSource(m.attachmentPath);
                mp.prepare();
                mp.start();

                // Visualizer para la forma real
                visualizer = new Visualizer(mp.getAudioSessionId());
                visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
                visualizer.setDataCaptureListener(
                        new Visualizer.OnDataCaptureListener() {
                            @Override
                            public void onWaveFormDataCapture(Visualizer vis, byte[] data, int rate) {
                                // Pasamos un OnSamplingListener vacío en lugar de null
                                waveform.setRawData(data, new OnSamplingListener() {
                                    @Override
                                    public void onComplete() {
                                        // no necesitamos hacer nada aquí
                                    }
                                });
                            }
                            @Override
                            public void onFftDataCapture(Visualizer vis, byte[] fft, int rate) {
                                // no usamos FFT
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        true,   // waveform
                        false   // fft
                );
                visualizer.setEnabled(true);

                // Arranca la actualización de progreso
                uiHandler.post(progressUpdater);
                btnPlay.setImageResource(R.mipmap.ic_pause);
                mp.setOnCompletionListener(player -> stopPlayback());
            } catch (IOException ignored) {}
        }



        private void stopPlayback() {
            if (visualizer != null) {
                visualizer.setEnabled(false);
                visualizer.release();
                visualizer = null;
            }
            if (mp != null) {
                mp.stop();
                mp.release();
                mp = null;
            }
            uiHandler.removeCallbacks(progressUpdater);
            waveform.setProgress(0f);
            btnPlay.setImageResource(R.mipmap.ic_play_arrow);
        }
    }
}
