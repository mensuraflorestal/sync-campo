package br.com.florestalrs.synccampo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DriveItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_FOLDER = 1;
    private static final int VIEW_TYPE_FILE = 2;

    public interface OnItemClickListener {
        void onFolderClick(DriveItem folder);
        void onFileClick(DriveItem file);
        void onFileOptionsClick(DriveItem file);
        void onFavoriteClick(DriveItem file);
    }

    private final Context context;
    private final List<DriveItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public DriveItemAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<DriveItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isFolder() ? VIEW_TYPE_FOLDER : VIEW_TYPE_FILE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_FOLDER) {
            View view = inflater.inflate(R.layout.item_folder, parent, false);
            return new FolderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_file, parent, false);
            return new FileViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DriveItem item = items.get(position);
        if (holder instanceof FolderViewHolder) {
            ((FolderViewHolder) holder).bind(item);
        } else if (holder instanceof FileViewHolder) {
            ((FileViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class FolderViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtName;
        private final TextView txtDetails;
        private final ImageButton btnFavorite;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txt_folder_name);
            txtDetails = itemView.findViewById(R.id.txt_folder_details);
            btnFavorite = itemView.findViewById(R.id.btn_folder_favorite);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFolderClick(items.get(pos));
                }
            });

            if (btnFavorite != null) {
                btnFavorite.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && listener != null) {
                        listener.onFavoriteClick(items.get(pos));
                    }
                });
            }
        }

        void bind(DriveItem item) {
            txtName.setText(item.getName());
            String info = item.getSubtitleInfo();
            if (info != null && !info.isEmpty()) {
                txtDetails.setText(info);
            } else {
                txtDetails.setText("Pasta de documentos");
            }
            if (btnFavorite != null) {
                btnFavorite.setImageResource(item.isFavorite() ? R.drawable.ic_star_filled : R.drawable.ic_star_border);
            }
        }
    }

    class FileViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtName;
        private final TextView txtCategory;
        private final TextView txtDetails;
        private final TextView txtOfflinePill;
        private final ImageButton btnFavorite;
        private final ImageButton btnOptions;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txt_file_name);
            txtCategory = itemView.findViewById(R.id.txt_file_category);
            txtDetails = itemView.findViewById(R.id.txt_file_details);
            txtOfflinePill = itemView.findViewById(R.id.txt_file_offline_pill);
            btnFavorite = itemView.findViewById(R.id.btn_file_favorite);
            btnOptions = itemView.findViewById(R.id.btn_file_options);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFileClick(items.get(pos));
                }
            });

            btnFavorite.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFavoriteClick(items.get(pos));
                }
            });

            btnOptions.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFileOptionsClick(items.get(pos));
                }
            });
        }

        void bind(DriveItem item) {
            String rawName = item.getName();
            String nameLower = rawName.toLowerCase();

            String extLabel = "DOC";
            String cleanTitle = rawName;
            int lastDot = rawName.lastIndexOf('.');
            if (lastDot > 0 && rawName.length() - lastDot <= 6) {
                extLabel = rawName.substring(lastDot + 1).toUpperCase();
                cleanTitle = rawName.substring(0, lastDot);
            }

            boolean isApr = nameLower.startsWith("apr") || nameLower.contains(" apr ") || item.getFolderPath().toLowerCase().contains("apr");
            boolean isFds = nameLower.startsWith("fds") || nameLower.contains("fispq") || item.getFolderPath().toLowerCase().contains("fds") || item.getFolderPath().toLowerCase().contains("fispq");

            if (cleanTitle.matches("(?i)^APR\\s*[-–:]\\s*.+")) {
                cleanTitle = cleanTitle.replaceFirst("(?i)^APR\\s*[-–:]\\s*", "").trim();
            } else if (cleanTitle.matches("(?i)^FDS\\s*[-–:]\\s*.+")) {
                cleanTitle = cleanTitle.replaceFirst("(?i)^FDS\\s*[-–:]\\s*", "").trim();
            } else if (cleanTitle.matches("(?i)^FISPQ\\s*[-–:]\\s*.+")) {
                cleanTitle = cleanTitle.replaceFirst("(?i)^FISPQ\\s*[-–:]\\s*", "").trim();
            }

            txtName.setText(cleanTitle);

            String extraSector = item.getSubtitleInfo();
            String sectorSuffix = (extraSector != null && !extraSector.isEmpty()) ? " • " + extraSector : "";

            if (isApr) {
                txtCategory.setText("APR • Segurança Operacional" + sectorSuffix);
            } else if (isFds) {
                txtCategory.setText("FDS • Segurança Química" + sectorSuffix);
            } else {
                txtCategory.setText("Documento Técnico" + sectorSuffix);
            }

            String size = item.getSizeFormatted();
            txtDetails.setText(extLabel + " • " + size);

            if (item.isCached()) {
                txtOfflinePill.setVisibility(View.VISIBLE);
                txtOfflinePill.setText("Disponível Offline");
                txtOfflinePill.setTextColor(0xFF2B3A2A);
                txtOfflinePill.setBackgroundResource(R.drawable.bg_pill_offline);
            } else {
                txtOfflinePill.setVisibility(View.VISIBLE);
                txtOfflinePill.setText("Baixar");
                txtOfflinePill.setTextColor(0xFF786848);
                txtOfflinePill.setBackgroundResource(R.drawable.bg_pill_pending);
            }

            if (item.isFavorite()) {
                btnFavorite.setImageResource(R.drawable.ic_star_filled);
            } else {
                btnFavorite.setImageResource(R.drawable.ic_star_border);
            }
        }
    }
}
