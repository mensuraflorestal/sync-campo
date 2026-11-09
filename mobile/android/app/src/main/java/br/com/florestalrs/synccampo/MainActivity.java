package br.com.florestalrs.synccampo;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements DriveItemAdapter.OnItemClickListener {

    private static final String PREFS_NAME = "sync_campo_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_CACHED_JSON = "cached_files_json";
    private static final String KEY_FAVORITES_SET = "favorite_file_ids";
    private static final String KEY_RECENTS_CSV = "recent_file_ids_csv";
    private static final String KEY_GRID_MODE = "ui_grid_mode";
    public static final String AUTH_TOKEN_HEADER = "X-SST-Token";
    public static final String AUTH_TOKEN_VALUE = "rsf_sec_9d81a4f028bc1723e4";

    // Componentes de UI
    private ImageButton btnHeaderBack;
    private View cardOfflineSummary;
    private TextView txtOfflineSummary;
    private TextView txtOfflineSubtitle;
    private TextView txtSyncCardStatus;
    private TextView txtSyncCardSubtitle;
    private View btnHeaderSync;
    private EditText editSearch;
    private ImageButton btnClearSearch;
    private ImageButton btnToggleLayout;
    private TextView txtNetworkBadge;
    private View layoutSyncProgress;
    private TextView txtSyncTitle;
    private TextView txtSyncCounter;
    private TextView txtSyncCurrentFile;
    private ProgressBar progressSync;

    private String activeFilter = "all";

    // Barra Inferior Flutuante (3 Pílulas: Setores, Offline, Fixados)
    private View navTabHome, navTabOffline, navTabSaved;
    private ImageView navIconHome, navIconOffline, navIconSaved;
    private TextView navTxtHome, navTxtOffline, navTxtSaved;

    // Navegação de Setores / Pastas
    private View btnFolderBack;
    private TextView btnFolderHome;
    private TextView txtBreadcrumbPath;
    private TextView txtItemsCount;

    // Lista e Estado Vazio
    private RecyclerView recyclerDrive;
    private DriveItemAdapter adapter;
    private View layoutEmptyState;
    private TextView txtEmptyTitle;
    private TextView txtEmptyDesc;
    private boolean isGridMode = false;

    private ExtendedFloatingActionButton fabSync;

    // Estado dos Dados
    private final List<DriveItem> allItems = new ArrayList<>();
    private final Set<String> favoriteIds = new HashSet<>();
    private final LinkedList<String> recentIds = new LinkedList<>();
    private final Stack<FolderCrumb> folderStack = new Stack<>();
    private String searchQuery = "";
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean isSyncing = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean isRefreshingCatalog = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean wasBgSyncActive = false;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Toast activeToast;

    private final Runnable hideSyncProgressRunnable = () -> {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (layoutSyncProgress != null && !isSyncing.get() && !prefs.getBoolean("bg_sync_active", false)) {
            layoutSyncProgress.setVisibility(View.GONE);
        }
    };

    private void showSingleToast(String msg, int duration) {
        runOnUiThread(() -> {
            if (activeToast != null) {
                activeToast.cancel();
            }
            activeToast = Toast.makeText(MainActivity.this, msg, duration);
            activeToast.show();
        });
    }

    private final Runnable liveSyncMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (txtNetworkBadge != null && !isOnline()) {
                if (!"Modo Offline".equals(txtNetworkBadge.getText().toString())) {
                    txtNetworkBadge.setText("Modo Offline");
                    txtNetworkBadge.setTextColor(0xFF717A71);
                }
            }
            if (!isSyncing.get()) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                boolean bgActive = prefs.getBoolean("bg_sync_active", false);
                if (bgActive) {
                    wasBgSyncActive = true;
                    uiHandler.removeCallbacks(hideSyncProgressRunnable);
                    int done = prefs.getInt("bg_sync_done", 0);
                    int total = Math.max(1, prefs.getInt("bg_sync_total", 1));
                    String currFile = prefs.getString("bg_sync_current_file", "Baixando...");
                    int pct = Math.min(100, (done * 100) / total);

                    if (layoutSyncProgress != null) layoutSyncProgress.setVisibility(View.VISIBLE);
                    if (txtSyncTitle != null) txtSyncTitle.setText("Sincronizando documentos...");
                    if (txtSyncCounter != null) txtSyncCounter.setText(done + " / " + total + " (" + pct + "%)");
                    if (txtSyncCurrentFile != null) txtSyncCurrentFile.setText(currFile);
                    if (progressSync != null) {
                        progressSync.setIndeterminate(false);
                        progressSync.setMax(total);
                        progressSync.setProgress(done);
                    }
                    if (txtSyncCardStatus != null) txtSyncCardStatus.setText("Baixando " + pct + "%");
                    if (txtSyncCardSubtitle != null) txtSyncCardSubtitle.setText(done + " de " + total + " arquivos");

                    // Recarrega os arquivos recem-salvos do disco para atualizar os cards de contagem ao vivo
                    loadCachedData();
                } else if (wasBgSyncActive) {
                    wasBgSyncActive = false;
                    loadCachedData();
                    if (layoutSyncProgress != null) {
                        if (txtSyncTitle != null) txtSyncTitle.setText("Sincronização concluída");
                        if (txtSyncCurrentFile != null) txtSyncCurrentFile.setText("Todos os arquivos estão disponíveis offline.");
                        uiHandler.removeCallbacks(hideSyncProgressRunnable);
                        uiHandler.postDelayed(hideSyncProgressRunnable, 3000);
                    }
                }
            }
            uiHandler.postDelayed(this, 1000);
        }
    };

    // Scanner nativo de QR Code
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String cleanUrl = cleanServerUrl(result.getContents());
                    if (!cleanUrl.isEmpty()) {
                        setServerUrl(cleanUrl);
                        Toast.makeText(this, "Conectado ao servidor", Toast.LENGTH_SHORT).show();
                        refreshDataFromServer();
                    }
                }
            }
    );

    private static class FolderCrumb {
        String id;
        String name;

        FolderCrumb(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadUserPreferences();
        initViews();
        setupFilters();
        setupRecyclerView();

        folderStack.push(new FolderCrumb("root", "Setores"));
        updateBreadcrumb();

        loadCachedData();
        refreshDataFromServer();

        if (getServerUrl().isEmpty()) {
            showServerSettingsDialog();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        showBackgroundGuideOrRequestExemption();
        SyncApp.scheduleBackgroundSync(this);
    }

    private boolean isAggressiveBatteryVendor() {
        String m = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER).toLowerCase();
        String[] vendors = {"xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus", "samsung", "motorola", "asus", "tecno", "infinix"};
        for (String v : vendors) {
            if (m.contains(v)) return true;
        }
        return false;
    }

    /** Na primeira abertura (com servidor configurado) explica como liberar o app para sincronizar com ele fechado. */
    private void showBackgroundGuideOrRequestExemption() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean alreadyShown = prefs.getBoolean("bg_guide_shown", false);
        if (alreadyShown || getServerUrl().isEmpty() || !isAggressiveBatteryVendor()) {
            requestBatteryOptimizationExemption();
            return;
        }

        String msg = "Para receber os documentos novos mesmo com o app fechado, libere o aplicativo nas configurações do aparelho:\n\n"
                + "1. Bateria: escolha \"Sem restrições\" (ou \"Não otimizar\").\n"
                + "2. Início automático: ative para este app (Xiaomi, Redmi, Poco, Oppo, Realme, Vivo).\n"
                + "3. Não use \"Forçar parada\" no app.\n\n"
                + "Sem isso, o sistema pode pausar a sincronização durante a noite.";

        new AlertDialog.Builder(this)
                .setTitle("Sincronização em segundo plano")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("Ajustar agora", (d, w) -> {
                    prefs.edit().putBoolean("bg_guide_shown", true).apply();
                    try {
                        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                            requestBatteryOptimizationExemption();
                        } else {
                            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        }
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Depois", (d, w) -> prefs.edit().putBoolean("bg_guide_shown", true).apply())
                .show();
    }

    private void loadUserPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> savedFavs = prefs.getStringSet(KEY_FAVORITES_SET, null);
        favoriteIds.clear();
        if (savedFavs != null) {
            favoriteIds.addAll(savedFavs);
        }

        recentIds.clear();
        String recentsCsv = prefs.getString(KEY_RECENTS_CSV, "");
        if (!recentsCsv.isEmpty()) {
            String[] parts = recentsCsv.split(",");
            for (String p : parts) {
                if (!p.trim().isEmpty() && !recentIds.contains(p.trim())) {
                    recentIds.add(p.trim());
                }
            }
        }

        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        boolean defaultGrid = screenWidthDp >= 580;
        isGridMode = prefs.getBoolean(KEY_GRID_MODE, defaultGrid);
    }

    private void saveLastSyncNow(String source) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putLong("last_sync_ts", System.currentTimeMillis())
                .putString("last_sync_source", source)
                .commit();
    }

    /** Mostra no card de Sincronização quando foi a última atualização (manual ou em segundo plano). */
    private void updateLastSyncLabel() {
        if (txtSyncCardSubtitle == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long ts = prefs.getLong("last_sync_ts", 0L);
        if (ts <= 0L) {
            txtSyncCardSubtitle.setText("Nunca sincronizado");
            return;
        }
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(ts);
        String hour = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(ts));
        String day;
        if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)) {
            day = "hoje";
        } else {
            now.add(java.util.Calendar.DAY_OF_YEAR, -1);
            if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)) {
                day = "ontem";
            } else {
                day = new java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(new java.util.Date(ts));
            }
        }
        txtSyncCardSubtitle.setText("Última: " + day + " " + hour);
    }

    private void saveFavorites() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_FAVORITES_SET, new HashSet<>(favoriteIds)).apply();
    }

    private void recordRecentFile(String fileId) {
        if (fileId == null || fileId.isEmpty()) return;
        recentIds.remove(fileId);
        recentIds.addFirst(fileId);
        while (recentIds.size() > 25) {
            recentIds.removeLast();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recentIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(recentIds.get(i));
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENTS_CSV, sb.toString())
                .apply();
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            } catch (Exception ignored) {}
        }
    }

    private void registerNetworkMonitor() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            if (networkCallback == null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        uiHandler.postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed() && isOnline()) {
                                refreshDataFromServer();
                            }
                        }, 600);
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        uiHandler.postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed() && !isOnline() && txtNetworkBadge != null) {
                                txtNetworkBadge.setText("Modo Offline");
                                txtNetworkBadge.setTextColor(0xFF717A71);
                            }
                        }, 300);
                    }
                };
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(request, networkCallback);
            }
        } catch (Exception ignored) {}
    }

    private void unregisterNetworkMonitor() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && networkCallback != null) {
                cm.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerNetworkMonitor();
        uiHandler.removeCallbacks(liveSyncMonitorRunnable);
        uiHandler.post(liveSyncMonitorRunnable);
        if (isSyncing.get()) {
            return;
        }
        loadCachedData();
        if (isOnline()) {
            refreshDataFromServer();
        } else if (txtNetworkBadge != null) {
            txtNetworkBadge.setText("Modo Offline");
            txtNetworkBadge.setTextColor(0xFF717A71);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterNetworkMonitor();
        uiHandler.removeCallbacks(liveSyncMonitorRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("fg_sync_active", false).commit();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.root_coordinator);
        View topHeader = findViewById(R.id.top_header_container);
        View bottomNav = findViewById(R.id.bottom_nav_bar);
        if (root == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (topHeader != null) {
                topHeader.setPadding(
                        dpToPx(18),
                        dpToPx(14) + bars.top,
                        dpToPx(18),
                        dpToPx(8)
                );
            }
            if (bottomNav != null && bottomNav.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
                lp.bottomMargin = dpToPx(14) + bars.bottom;
                bottomNav.setLayoutParams(lp);
            }
            if (recyclerDrive != null) {
                recyclerDrive.setPadding(
                        dpToPx(14),
                        dpToPx(4),
                        dpToPx(14),
                        dpToPx(96) + bars.bottom
                );
            }
            return windowInsets;
        });
    }

    private void initViews() {
        btnHeaderBack = findViewById(R.id.btn_header_back);
        cardOfflineSummary = findViewById(R.id.card_offline_summary);
        txtOfflineSummary = findViewById(R.id.txt_offline_summary);
        txtOfflineSubtitle = findViewById(R.id.txt_offline_subtitle);
        txtSyncCardStatus = findViewById(R.id.txt_sync_card_status);
        txtSyncCardSubtitle = findViewById(R.id.txt_sync_card_subtitle);
        btnHeaderSync = findViewById(R.id.btn_header_sync);
        editSearch = findViewById(R.id.edit_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        btnToggleLayout = findViewById(R.id.btn_toggle_layout);
        txtNetworkBadge = findViewById(R.id.txt_network_badge);
        layoutSyncProgress = findViewById(R.id.layout_sync_progress);
        txtSyncTitle = findViewById(R.id.txt_sync_title);
        txtSyncCounter = findViewById(R.id.txt_sync_counter);
        txtSyncCurrentFile = findViewById(R.id.txt_sync_current_file);
        progressSync = findViewById(R.id.progress_sync);

        navTabHome = findViewById(R.id.nav_tab_home);
        navTabOffline = findViewById(R.id.nav_tab_offline);
        navTabSaved = findViewById(R.id.nav_tab_saved);
        navIconHome = findViewById(R.id.nav_icon_home);
        navIconOffline = findViewById(R.id.nav_icon_offline);
        navIconSaved = findViewById(R.id.nav_icon_saved);
        navTxtHome = findViewById(R.id.nav_txt_home);
        navTxtOffline = findViewById(R.id.nav_txt_offline);
        navTxtSaved = findViewById(R.id.nav_txt_saved);

        btnFolderBack = findViewById(R.id.btn_folder_back);
        btnFolderHome = findViewById(R.id.btn_folder_home);
        txtBreadcrumbPath = findViewById(R.id.txt_breadcrumb_path);
        txtItemsCount = findViewById(R.id.txt_items_count);

        recyclerDrive = findViewById(R.id.recycler_drive);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        txtEmptyTitle = findViewById(R.id.txt_empty_title);
        txtEmptyDesc = findViewById(R.id.txt_empty_desc);
        fabSync = findViewById(R.id.fab_sync);

        setupWindowInsets();

        // Engrenagem oculta em producao: acesso a configuracoes somente por clique longo no badge para TI/Admin
        txtNetworkBadge.setOnLongClickListener(v -> {
            showServerSettingsDialog();
            return true;
        });
        if (cardOfflineSummary != null) {
            cardOfflineSummary.setOnClickListener(v -> selectFilterMode("offline"));
        }
        if (btnHeaderSync != null) {
            btnHeaderSync.setOnClickListener(v -> syncAllFiles(false));
        }
        if (fabSync != null) {
            fabSync.setOnClickListener(v -> syncAllFiles(false));
        }

        updateLayoutToggleIcon();
        btnToggleLayout.setOnClickListener(v -> {
            isGridMode = !isGridMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_GRID_MODE, isGridMode).apply();
            updateLayoutToggleIcon();
            applyRecyclerLayoutManager();
        });

        View.OnClickListener backAction = v -> {
            if (!searchQuery.isEmpty()) {
                editSearch.setText("");
                searchQuery = "";
                updateBreadcrumb();
                applyFilterAndRender();
            } else if (!"all".equals(activeFilter)) {
                selectFilterMode("all");
            } else if (folderStack.size() > 1) {
                navigateBackFolder();
            }
        };

        if (btnHeaderBack != null) {
            btnHeaderBack.setOnClickListener(backAction);
        }
        if (btnFolderBack != null) {
            btnFolderBack.setOnClickListener(backAction);
        }
        if (btnFolderHome != null) {
            btnFolderHome.setOnClickListener(v -> navigateHomeRoot());
        }
        txtBreadcrumbPath.setOnClickListener(v -> {
            if (folderStack.size() > 1 && "all".equals(activeFilter)) {
                navigateBackFolder();
            }
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilterAndRender();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
            searchQuery = "";
            btnClearSearch.setVisibility(View.GONE);
            applyFilterAndRender();
        });
    }

    private void updateLayoutToggleIcon() {
        if (btnToggleLayout == null) return;
        if (isGridMode) {
            btnToggleLayout.setImageResource(R.drawable.ic_list_view);
        } else {
            btnToggleLayout.setImageResource(R.drawable.ic_grid_view);
        }
    }

    private void applyRecyclerLayoutManager() {
        if (recyclerDrive == null) return;
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        if (isGridMode) {
            int columns = screenWidthDp >= 840 ? 3 : 2;
            recyclerDrive.setLayoutManager(new GridLayoutManager(this, columns));
        } else {
            recyclerDrive.setLayoutManager(new LinearLayoutManager(this));
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyRecyclerLayoutManager();
    }

    private void selectFilterMode(String filter) {
        activeFilter = filter;
        updateBottomNavUi();
        updateBreadcrumb();
        applyFilterAndRender();
        if (recyclerDrive != null) {
            recyclerDrive.scrollToPosition(0);
        }
    }

    private void setupFilters() {
        if (navTabHome != null) {
            navTabHome.setOnClickListener(v -> {
                if ("all".equals(activeFilter) && folderStack.size() > 1) {
                    navigateHomeRoot();
                } else {
                    selectFilterMode("all");
                }
            });
        }
        if (navTabOffline != null) {
            navTabOffline.setOnClickListener(v -> selectFilterMode("offline"));
        }
        if (navTabSaved != null) {
            navTabSaved.setOnClickListener(v -> selectFilterMode("favorites"));
        }

        updateBottomNavUi();
    }

    private void updateBottomNavUi() {
        boolean isOffline = "offline".equals(activeFilter);
        boolean isSaved = "favorites".equals(activeFilter) || "recents".equals(activeFilter);
        boolean isHome = !isOffline && !isSaved;

        setBottomTabStyle(navTabHome, navIconHome, navTxtHome, isHome);
        setBottomTabStyle(navTabOffline, navIconOffline, navTxtOffline, isOffline);
        setBottomTabStyle(navTabSaved, navIconSaved, navTxtSaved, isSaved);
    }

    private void setBottomTabStyle(View tabView, ImageView iconView, TextView titleView, boolean active) {
        if (tabView == null || titleView == null) return;
        if (active) {
            tabView.setBackgroundResource(R.drawable.bg_nav_item_active);
            titleView.setTextColor(0xFFF6F6F2);
            if (iconView != null) iconView.setColorFilter(0xFFE2C178);
        } else {
            tabView.setBackgroundResource(0);
            titleView.setTextColor(0xFF8A9A88);
            if (iconView != null) iconView.setColorFilter(0xFF8A9A88);
        }
    }

    private void setupRecyclerView() {
        applyRecyclerLayoutManager();
        adapter = new DriveItemAdapter(this, this);
        recyclerDrive.setAdapter(adapter);
    }

    private File getDocsDir() {
        File dir = new File(getFilesDir(), "campo_docs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
        return false;
    }

    private String getServerUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String url = prefs.getString(KEY_SERVER_URL, "").trim();
        if (url.isEmpty() || url.contains("trycloudflare.com") || url.contains("teste.florestalrs.com.br") || url.contains("192.168.") || url.contains("10.0.")) {
            prefs.edit().putString(KEY_SERVER_URL, "https://sst.florestalrs.com.br").commit();
            return "https://sst.florestalrs.com.br";
        }
        return url;
    }

    private void setServerUrl(String url) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, url).commit();
        SyncApp.scheduleBackgroundSync(this);
    }

    public static void saveCatalogBackupFile(Context context, String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return;
        try {
            File backup = new File(context.getFilesDir(), "catalog_offline.json");
            File tmp = new File(context.getFilesDir(), "catalog_offline.json.tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(jsonStr.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            if (backup.exists()) backup.delete();
            tmp.renameTo(backup);
        } catch (Exception ignored) {}
    }

    public static String readCatalogBackupFile(Context context) {
        try {
            File backup = new File(context.getFilesDir(), "catalog_offline.json");
            if (backup.exists() && backup.length() > 0) {
                java.io.FileInputStream fis = new java.io.FileInputStream(backup);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static String cleanServerUrl(String raw) {
        if (raw == null) return "";
        String str = raw.trim();
        str = str.replaceAll("/+app\\.apk(\\?.*)?$", "");
        str = str.replaceAll("/+sync_campo\\.apk(\\?.*)?$", "");
        str = str.replaceAll("/+api/.*$", "");
        if (!str.startsWith("http://") && !str.startsWith("https://")) {
            str = "https://" + str;
        }
        try {
            URL url = new URL(str);
            String port = (url.getPort() != -1 && url.getPort() != url.getDefaultPort()) ? ":" + url.getPort() : "";
            return url.getProtocol() + "://" + url.getHost() + port;
        } catch (Exception e) {
            return str.replaceAll("/+$", "");
        }
    }

    public static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "documento";
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.length() > 110) {
            int dot = clean.lastIndexOf('.');
            if (dot > 0 && clean.length() - dot <= 10) {
                String ext = clean.substring(dot);
                clean = clean.substring(0, 100) + ext;
            } else {
                clean = clean.substring(0, 110);
            }
        }
        return clean;
    }

    private File getLocalFile(File storageDir, String id, String name) {
        return new File(storageDir, id + "_" + sanitizeFileName(name));
    }

    // ==========================================
    // CARREGAMENTO E SINCRONIZAÇÃO
    // ==========================================
    private void loadCachedData() {
        String cachedJson = readCatalogBackupFile(this);
        if (cachedJson.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            cachedJson = prefs.getString(KEY_CACHED_JSON, "");
        }

        if (!cachedJson.isEmpty()) {
            List<DriveItem> items = parseItems(cachedJson);
            setAllItems(items);
        } else {
            applyFilterAndRender();
        }
    }

    private void setAllItems(List<DriveItem> newItems) {
        synchronized (allItems) {
            allItems.clear();
            if (newItems != null) {
                allItems.addAll(newItems);
            }
        }
        applyFilterAndRender();
    }

    private boolean isItemCachedValid(DriveItem item, File localFile, SharedPreferences syncPrefs) {
        if (localFile == null || !localFile.exists() || localFile.length() <= 0) return false;
        String savedVersion = syncPrefs.getString("ver_" + item.getId(), "");
        long savedSize = syncPrefs.getLong("size_" + item.getId(), -1L);
        if (!savedVersion.isEmpty() && !savedVersion.equals(item.getVersionKey())) return false;
        if (savedSize > 0) return localFile.length() == savedSize;
        return item.getSize() <= 0 || localFile.length() == item.getSize();
    }

    private List<DriveItem> parseItems(String jsonString) {
        List<DriveItem> list = new ArrayList<>();
        if (jsonString == null || jsonString.isEmpty()) return list;

        try {
            JSONObject json = new JSONObject(jsonString);
            JSONArray arr = json.optJSONArray("files");
            if (arr == null) return list;

            File storageDir = getDocsDir();
            SharedPreferences syncPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                DriveItem item = new DriveItem();
                item.setId(obj.optString("id"));
                item.setName(obj.optString("name"));
                item.setMimeType(obj.optString("mimeType"));
                item.setSize(obj.optLong("size", 0));
                item.setSizeFormatted(obj.optString("size_formatted", "--"));
                item.setModifiedTime(obj.optString("modifiedTime", ""));
                item.setVersionKey(obj.optString("checksum", "") + "_" + obj.optString("modifiedTime", ""));

                boolean isF = obj.optBoolean("is_folder", false) || "application/vnd.google-apps.folder".equals(item.getMimeType());
                item.setFolder(isF);

                String folderPath = obj.optString("folder_path", obj.optString("folder", ""));
                item.setFolderPath(folderPath);

                String pId = obj.optString("parentId", "");
                if (pId.isEmpty()) {
                    pId = (folderPath.isEmpty() || "Raiz".equals(folderPath)) ? "root" : folderPath;
                }
                item.setParentId(pId);

                item.setFavorite(favoriteIds.contains(item.getId()));

                File localFile = getLocalFile(storageDir, item.getId(), item.getName());
                if (!isF && isItemCachedValid(item, localFile, syncPrefs)) {
                    item.setCached(true);
                    item.setLocalFile(localFile);
                } else {
                    item.setCached(false);
                }

                list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private boolean hasPendingDownloads(List<DriveItem> items) {
        if (items == null) return false;
        SharedPreferences syncPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        File storageDir = getDocsDir();
        for (DriveItem item : items) {
            if (item.isFolder()) continue;
            File localFile = getLocalFile(storageDir, item.getId(), item.getName());
            if (!isItemCachedValid(item, localFile, syncPrefs)) {
                return true;
            }
        }
        return false;
    }

    private void refreshDataFromServer() {
        loadCachedData();

        if (!isOnline()) {
            txtNetworkBadge.setText("Modo Offline");
            txtNetworkBadge.setTextColor(0xFF717A71);
            return;
        }

        String serverUrl = getServerUrl();
        if (serverUrl.isEmpty()) {
            txtNetworkBadge.setText("Sem Servidor");
            txtNetworkBadge.setTextColor(0xFF717A71);
            return;
        }

        if (isSyncing.get() || !isRefreshingCatalog.compareAndSet(false, true)) {
            return;
        }

        txtNetworkBadge.setText("Conectando...");
        txtNetworkBadge.setTextColor(0xFF2B3A2A);

        executor.execute(() -> {
            try {
                URL statusUrl = new URL(serverUrl + "/api/status");
                HttpURLConnection statusConn = (HttpURLConnection) statusUrl.openConnection();
                statusConn.setRequestProperty(AUTH_TOKEN_HEADER, AUTH_TOKEN_VALUE);
                statusConn.setConnectTimeout(10000);
                statusConn.setReadTimeout(12000);

                if (statusConn.getResponseCode() == 200) {
                    runOnUiThread(() -> {
                        txtNetworkBadge.setText("Online");
                        txtNetworkBadge.setTextColor(0xFF2B3A2A);
                    });
                } else {
                    runOnUiThread(() -> {
                        txtNetworkBadge.setText("Modo Offline");
                        txtNetworkBadge.setTextColor(0xFF717A71);
                    });
                }

                URL filesUrl = new URL(serverUrl + "/api/files");
                HttpURLConnection filesConn = (HttpURLConnection) filesUrl.openConnection();
                filesConn.setRequestProperty(AUTH_TOKEN_HEADER, AUTH_TOKEN_VALUE);
                filesConn.setConnectTimeout(10000);
                filesConn.setReadTimeout(15000);

                if (filesConn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(filesConn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String jsonStr = response.toString();
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_CACHED_JSON, jsonStr).commit();
                    saveCatalogBackupFile(MainActivity.this, jsonStr);
                    List<DriveItem> loaded = parseItems(jsonStr);
                    final boolean needsAutoSync = hasPendingDownloads(loaded);

                    if (!isSyncing.get()) {
                        runOnUiThread(() -> {
                            setAllItems(loaded);
                            boolean bgActive = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("bg_sync_active", false);
                            if (needsAutoSync && !bgActive) {
                                syncAllFiles(true);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    txtNetworkBadge.setText("Modo Offline");
                    txtNetworkBadge.setTextColor(0xFF717A71);
                });
            } finally {
                isRefreshingCatalog.set(false);
            }
        });
    }

    private void syncAllFiles(boolean silent) {
        if (!isOnline()) {
            showSingleToast("Sem conexão de rede no momento.", Toast.LENGTH_SHORT);
            return;
        }

        String serverUrl = getServerUrl();
        if (serverUrl.isEmpty()) {
            showServerSettingsDialog();
            return;
        }

        if (!isSyncing.compareAndSet(false, true)) {
            if (!silent) {
                showSingleToast("Sincronização já está em andamento...", Toast.LENGTH_SHORT);
            }
            return;
        }

        // Interrompe imediatamente qualquer sincronizacao em segundo plano para evitar colisao de arquivos
        SharedPreferences initPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        initPrefs.edit()
                .putBoolean("fg_sync_active", true)
                .putBoolean("bg_sync_active", false)
                .commit();
        wasBgSyncActive = false;
        SyncApp.cancelImmediateBackgroundSync(this);

        runOnUiThread(() -> {
            uiHandler.removeCallbacks(hideSyncProgressRunnable);
            if (layoutSyncProgress != null) layoutSyncProgress.setVisibility(View.VISIBLE);
            if (txtSyncTitle != null) txtSyncTitle.setText("Conectando ao servidor...");
            if (txtSyncCounter != null) txtSyncCounter.setText("Verificando...");
            if (txtSyncCurrentFile != null) txtSyncCurrentFile.setText("Consultando catálogo de documentos...");
            if (progressSync != null) {
                progressSync.setIndeterminate(true);
            }
            if (txtSyncCardStatus != null) txtSyncCardStatus.setText("Verificando...");
            if (txtSyncCardSubtitle != null) txtSyncCardSubtitle.setText("Consultando servidor...");
            if (btnHeaderSync != null) {
                btnHeaderSync.setEnabled(false);
                btnHeaderSync.setAlpha(0.6f);
            }
            if (fabSync != null) {
                fabSync.setEnabled(false);
            }
        });

        syncExecutor.execute(() -> {
            try {
                String jsonStr = null;

                try {
                    URL filesUrl = new URL(serverUrl + "/api/files");
                    HttpURLConnection filesConn = (HttpURLConnection) filesUrl.openConnection();
                    filesConn.setRequestProperty(AUTH_TOKEN_HEADER, AUTH_TOKEN_VALUE);
                    filesConn.setConnectTimeout(15000);
                    filesConn.setReadTimeout(25000);

                    if (filesConn.getResponseCode() == 200) {
                        runOnUiThread(() -> {
                            if (txtNetworkBadge != null) {
                                txtNetworkBadge.setText("Online");
                                txtNetworkBadge.setTextColor(0xFF2B3A2A);
                            }
                        });
                        BufferedReader reader = new BufferedReader(new InputStreamReader(filesConn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        jsonStr = response.toString();
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_CACHED_JSON, jsonStr).commit();
                        saveCatalogBackupFile(MainActivity.this, jsonStr);
                    }
                } catch (Exception e) {
                    android.util.Log.e("Syncamp", "Erro ao obter lista de arquivos: " + e.getMessage());
                }

                if (jsonStr == null) {
                    jsonStr = readCatalogBackupFile(MainActivity.this);
                    if (jsonStr.isEmpty()) {
                        jsonStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CACHED_JSON, "");
                    }
                }

                List<DriveItem> targetItems = parseItems(jsonStr);
                File storageDir = getDocsDir();

                List<DriveItem> filesToDownload = new ArrayList<>();
                SharedPreferences syncPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor verEditor = syncPrefs.edit();
                boolean verEdited = false;

                for (DriveItem item : targetItems) {
                    if (item.isFolder()) continue;
                    File localFile = getLocalFile(storageDir, item.getId(), item.getName());
                    if (isItemCachedValid(item, localFile, syncPrefs)) {
                        if (syncPrefs.getString("ver_" + item.getId(), "").isEmpty()) {
                            verEditor.putString("ver_" + item.getId(), item.getVersionKey());
                            verEditor.putLong("size_" + item.getId(), localFile.length());
                            verEdited = true;
                        }
                        item.setCached(true);
                        item.setLocalFile(localFile);
                    } else {
                        item.setCached(false);
                        filesToDownload.add(item);
                    }
                }
                if (verEdited) {
                    verEditor.apply();
                }

                runOnUiThread(() -> setAllItems(targetItems));

                final int totalToDownload = filesToDownload.size();
                java.util.concurrent.atomic.AtomicInteger downloadedCounter = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger failedCounter = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger completedCounter = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicLong lastUiRenderTime = new java.util.concurrent.atomic.AtomicLong(0L);

                if (totalToDownload == 0) {
                    syncPrefs.edit().putBoolean("fg_sync_active", false).commit();
                    isSyncing.set(false);
                    saveLastSyncNow("manual");
                    runOnUiThread(() -> {
                        uiHandler.removeCallbacks(hideSyncProgressRunnable);
                        if (layoutSyncProgress != null) layoutSyncProgress.setVisibility(View.GONE);
                        if (btnHeaderSync != null) {
                            btnHeaderSync.setEnabled(true);
                            btnHeaderSync.setAlpha(1.0f);
                        }
                        if (fabSync != null) fabSync.setEnabled(true);
                        setAllItems(targetItems);
                        if (!silent) {
                            showSingleToast("Todos os documentos já estão atualizados.", Toast.LENGTH_SHORT);
                        }
                    });
                    return;
                }

                runOnUiThread(() -> {
                    uiHandler.removeCallbacks(hideSyncProgressRunnable);
                    if (layoutSyncProgress != null) layoutSyncProgress.setVisibility(View.VISIBLE);
                    if (txtSyncTitle != null) txtSyncTitle.setText("Baixando documentos...");
                    if (txtSyncCounter != null) txtSyncCounter.setText("0 / " + totalToDownload + " (0%)");
                    if (txtSyncCurrentFile != null) txtSyncCurrentFile.setText("Iniciando transferência...");
                    if (progressSync != null) {
                        progressSync.setIndeterminate(false);
                        progressSync.setMax(totalToDownload);
                        progressSync.setProgress(0);
                    }
                    if (txtSyncCardStatus != null) txtSyncCardStatus.setText("Baixando 0%");
                    if (txtSyncCardSubtitle != null) txtSyncCardSubtitle.setText("0 de " + totalToDownload + " arquivos");
                });

                java.util.concurrent.ExecutorService dlPool = java.util.concurrent.Executors.newFixedThreadPool(4);

                for (DriveItem item : filesToDownload) {
                    dlPool.execute(() -> {
                        File localFile = getLocalFile(storageDir, item.getId(), item.getName());
                        File tempFile = new File(storageDir, localFile.getName() + ".fg_" + Thread.currentThread().getId() + ".part");
                        boolean fileSuccess = false;
                        try {
                            for (int attempt = 0; attempt < 3 && !fileSuccess; attempt++) {
                                if (attempt > 0) {
                                    try { Thread.sleep(350L * attempt); } catch (InterruptedException ignored) {}
                                }
                                HttpURLConnection conn = null;
                                try {
                                    if (tempFile.exists()) tempFile.delete();
                                    URL dlUrl = new URL(serverUrl + "/api/files/" + item.getId() + "/download");
                                    conn = (HttpURLConnection) dlUrl.openConnection();
                                    conn.setRequestProperty(AUTH_TOKEN_HEADER, AUTH_TOKEN_VALUE);
                                    conn.setConnectTimeout(20000);
                                    conn.setReadTimeout(60000);

                                    if (conn.getResponseCode() == 200) {
                                        long expectedLen = conn.getContentLengthLong();
                                        try (InputStream in = conn.getInputStream();
                                             FileOutputStream out = new FileOutputStream(tempFile)) {
                                            byte[] buffer = new byte[32768];
                                            int len;
                                            while ((len = in.read(buffer)) != -1) {
                                                out.write(buffer, 0, len);
                                            }
                                            out.flush();
                                        }

                                        long downloadedLen = tempFile.length();
                                        boolean validLen = downloadedLen > 0 && (expectedLen <= 0 || downloadedLen == expectedLen);
                                        if (validLen) {
                                            if (localFile.exists()) localFile.delete();
                                            if (tempFile.renameTo(localFile)) {
                                                long finalSize = localFile.length();
                                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                                        .putString("ver_" + item.getId(), item.getVersionKey())
                                                        .putLong("size_" + item.getId(), finalSize)
                                                        .apply();
                                                item.setCached(true);
                                                item.setLocalFile(localFile);
                                                downloadedCounter.incrementAndGet();
                                                fileSuccess = true;
                                            }
                                        }
                                    }
                                } catch (Exception attemptErr) {
                                    // Tenta novamente ate 3 vezes
                                } finally {
                                    if (conn != null) {
                                        try { conn.disconnect(); } catch (Exception ignored) {}
                                    }
                                    if (tempFile.exists()) {
                                        tempFile.delete();
                                    }
                                }
                            }
                            if (!fileSuccess) {
                                failedCounter.incrementAndGet();
                            }
                        } finally {
                            if (tempFile.exists()) {
                                tempFile.delete();
                            }

                            int done = completedCounter.incrementAndGet();
                            int pct = Math.min(100, (done * 100) / Math.max(1, totalToDownload));
                            final boolean ok = fileSuccess;
                            long nowMs = System.currentTimeMillis();
                            long prevMs = lastUiRenderTime.get();
                            final boolean shouldRenderList = (done == totalToDownload) || (nowMs - prevMs >= 400 && lastUiRenderTime.compareAndSet(prevMs, nowMs));

                            runOnUiThread(() -> {
                                if (!isSyncing.get()) return;
                                if (progressSync != null) progressSync.setProgress(done);
                                if (txtSyncCounter != null) txtSyncCounter.setText(done + " / " + totalToDownload + " (" + pct + "%)");
                                if (txtSyncCurrentFile != null) {
                                    if (ok) {
                                        txtSyncCurrentFile.setText(item.getName());
                                    } else {
                                        txtSyncCurrentFile.setText("Falha ao baixar: " + item.getName());
                                    }
                                }
                                if (txtSyncCardStatus != null) txtSyncCardStatus.setText("Baixando " + pct + "%");
                                if (txtSyncCardSubtitle != null) txtSyncCardSubtitle.setText(done + " de " + totalToDownload + " arquivos");
                                if (shouldRenderList) {
                                    applyFilterAndRender();
                                }
                            });
                        }
                    });
                }

                dlPool.shutdown();
                dlPool.awaitTermination(10, java.util.concurrent.TimeUnit.MINUTES);

                // Garante gravacao sincrona das versoes/tamanhos antes de liberar o estado
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("fg_sync_active", false).commit();

                final int count = downloadedCounter.get();
                final int failed = failedCounter.get();
                isSyncing.set(false);
                if (failed == 0) saveLastSyncNow("manual");

                runOnUiThread(() -> {
                    if (btnHeaderSync != null) {
                        btnHeaderSync.setEnabled(true);
                        btnHeaderSync.setAlpha(1.0f);
                    }
                    if (fabSync != null) fabSync.setEnabled(true);
                    setAllItems(targetItems);
                    if (progressSync != null) progressSync.setProgress(totalToDownload);

                    if (failed == 0) {
                        if (txtSyncTitle != null) txtSyncTitle.setText("Sincronização concluída");
                        if (txtSyncCounter != null) txtSyncCounter.setText(count + " / " + totalToDownload + " (100%)");
                        if (txtSyncCurrentFile != null) txtSyncCurrentFile.setText("Todos os arquivos estão disponíveis offline.");
                        if (!silent) {
                            showSingleToast("Sincronização concluída (" + count + " arquivos).", Toast.LENGTH_LONG);
                        }
                    } else {
                        if (txtSyncTitle != null) txtSyncTitle.setText("Sincronização parcial");
                        if (txtSyncCounter != null) txtSyncCounter.setText(count + " salvos, " + failed + " pendentes");
                        if (txtSyncCurrentFile != null) txtSyncCurrentFile.setText("Toque em Sincronizar novamente para concluir os pendentes.");
                        if (!silent) {
                            showSingleToast("Sincronização parcial: " + count + " salvos, " + failed + " pendentes.", Toast.LENGTH_LONG);
                        }
                    }

                    uiHandler.removeCallbacks(hideSyncProgressRunnable);
                    uiHandler.postDelayed(hideSyncProgressRunnable, 4000);
                });

            } catch (Exception generalErr) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("fg_sync_active", false).commit();
                isSyncing.set(false);
                runOnUiThread(() -> {
                    if (layoutSyncProgress != null) layoutSyncProgress.setVisibility(View.GONE);
                    if (btnHeaderSync != null) {
                        btnHeaderSync.setEnabled(true);
                        btnHeaderSync.setAlpha(1.0f);
                    }
                    if (fabSync != null) fabSync.setEnabled(true);
                    applyFilterAndRender();
                    showSingleToast("Erro durante a sincronização.", Toast.LENGTH_SHORT);
                });
            }
        });
    }

    // ==========================================
    // NAVEGAÇÃO DE SETORES E FILTRAGEM
    // ==========================================
    private void navigateHomeRoot() {
        while (folderStack.size() > 1) {
            folderStack.pop();
        }
        if (!"all".equals(activeFilter)) {
            activeFilter = "all";
            updateBottomNavUi();
        }
        if (!searchQuery.isEmpty()) {
            editSearch.setText("");
            searchQuery = "";
        }
        updateBreadcrumb();
        applyFilterAndRender();
    }

    private void updateBreadcrumb() {
        boolean isDeepFolder = folderStack.size() > 1 && "all".equals(activeFilter) && searchQuery.isEmpty();
        boolean canGoBack = isDeepFolder || !"all".equals(activeFilter) || !searchQuery.isEmpty();

        if (btnHeaderBack != null) {
            btnHeaderBack.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
        }
        if (btnFolderBack != null) {
            btnFolderBack.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
        }
        if (btnFolderHome != null) {
            boolean showHomeBtn = folderStack.size() > 2 && "all".equals(activeFilter) && searchQuery.isEmpty();
            btnFolderHome.setVisibility(showHomeBtn ? View.VISIBLE : View.GONE);
        }

        if (!searchQuery.isEmpty()) {
            txtBreadcrumbPath.setText("Pesquisa: \"" + searchQuery + "\"");
            return;
        }

        if (!"all".equals(activeFilter)) {
            switch (activeFilter) {
                case "favorites":
                    txtBreadcrumbPath.setText("Documentos Fixados");
                    break;
                case "offline":
                    txtBreadcrumbPath.setText("Disponíveis Offline");
                    break;
                default:
                    txtBreadcrumbPath.setText("Setores");
                    break;
            }
            return;
        }

        if (folderStack.isEmpty()) {
            txtBreadcrumbPath.setText("Setores");
            return;
        }

        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < folderStack.size(); i++) {
            if (i > 0) pathBuilder.append("  /  ");
            pathBuilder.append(folderStack.get(i).name);
        }
        txtBreadcrumbPath.setText(pathBuilder.toString());
    }

    private void navigateBackFolder() {
        if (folderStack.size() > 1) {
            folderStack.pop();
            updateBreadcrumb();
            applyFilterAndRender();
        }
    }

    @Override
    public void onBackPressed() {
        if (!searchQuery.isEmpty()) {
            editSearch.setText("");
            searchQuery = "";
            updateBreadcrumb();
            applyFilterAndRender();
            return;
        }
        if (!"all".equals(activeFilter)) {
            selectFilterMode("all");
            return;
        }
        if (folderStack.size() > 1) {
            navigateBackFolder();
            return;
        }
        super.onBackPressed();
    }

    private String extractShortSectorName(DriveItem item) {
        String folderPath = item.getFolderPath();
        if (folderPath == null || folderPath.isEmpty() || "Raiz".equalsIgnoreCase(folderPath)) {
            return "";
        }
        int slash = folderPath.lastIndexOf('/');
        if (slash >= 0 && slash < folderPath.length() - 1) {
            return folderPath.substring(slash + 1);
        }
        return folderPath;
    }

    private void applyFilterAndRender() {
        List<DriveItem> sourceList;
        synchronized (allItems) {
            sourceList = new ArrayList<>(allItems);
        }

        int totalFilesCount = 0;
        int cachedFilesCount = 0;
        int favCount = 0;
        Map<String, Integer> folderChildCount = new HashMap<>();
        Map<String, DriveItem> idMap = new HashMap<>();

        for (DriveItem item : sourceList) {
            idMap.put(item.getId(), item);
            item.setFavorite(favoriteIds.contains(item.getId()));

            String pId = item.getParentId();
            folderChildCount.put(pId, folderChildCount.getOrDefault(pId, 0) + 1);

            if (item.isFavorite()) favCount++;
            if (!item.isFolder()) {
                totalFilesCount++;
                if (item.isCached()) cachedFilesCount++;
            }
        }

        boolean syncActive = isSyncing.get() || getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("bg_sync_active", false);

        // Atualiza Bento Cards de Resumo no Topo
        if (txtOfflineSummary != null) {
            if (totalFilesCount == 0) {
                txtOfflineSummary.setText("0");
                if (txtOfflineSubtitle != null) txtOfflineSubtitle.setText("Nenhum arquivo sincronizado");
                if (!syncActive && txtSyncCardStatus != null) txtSyncCardStatus.setText("Sincronizar");
            } else if (cachedFilesCount >= totalFilesCount) {
                txtOfflineSummary.setText(String.valueOf(totalFilesCount));
                if (txtOfflineSubtitle != null) txtOfflineSubtitle.setText("100% prontos offline");
                if (!syncActive && txtSyncCardStatus != null) txtSyncCardStatus.setText("Atualizado");
            } else {
                int pending = totalFilesCount - cachedFilesCount;
                txtOfflineSummary.setText(cachedFilesCount + " / " + totalFilesCount);
                if (txtOfflineSubtitle != null) txtOfflineSubtitle.setText(pending + " pendentes de download");
                if (!syncActive && txtSyncCardStatus != null) txtSyncCardStatus.setText("Baixar (" + pending + ")");
            }
        }

        if (navTxtSaved != null) {
            navTxtSaved.setText(favCount > 0 ? "Fixados (" + favCount + ")" : "Fixados");
        }

        if (!syncActive) {
            updateLastSyncLabel();
        }

        updateBreadcrumb();
        updateBottomNavUi();

        List<DriveItem> filtered = new ArrayList<>();
        String currentParentId = folderStack.isEmpty() ? "root" : folderStack.peek().id;
        boolean isGlobalView = !searchQuery.isEmpty() || !"all".equals(activeFilter);

        if ("recents".equals(activeFilter) && searchQuery.isEmpty()) {
            for (String recentId : recentIds) {
                DriveItem item = idMap.get(recentId);
                if (item != null && !item.isFolder()) {
                    item.setSubtitleInfo(extractShortSectorName(item));
                    filtered.add(item);
                }
            }
        } else {
            for (DriveItem item : sourceList) {
                if (!searchQuery.isEmpty()) {
                    String q = searchQuery.toLowerCase();
                    boolean matchesName = item.getName().toLowerCase().contains(q);
                    boolean matchesFolder = item.getFolderPath().toLowerCase().contains(q);
                    if (!matchesName && !matchesFolder) continue;
                } else {
                    if ("all".equals(activeFilter)) {
                        if (!item.getParentId().equals(currentParentId)) continue;
                    }
                }

                if ("favorites".equals(activeFilter)) {
                    if (!item.isFavorite()) continue;
                } else if ("apr".equals(activeFilter)) {
                    if (item.isFolder()) continue;
                    String n = item.getName().toLowerCase();
                    String f = item.getFolderPath().toLowerCase();
                    if (!n.contains("apr") && !f.contains("apr")) continue;
                } else if ("fds".equals(activeFilter)) {
                    if (item.isFolder()) continue;
                    String n = item.getName().toLowerCase();
                    String f = item.getFolderPath().toLowerCase();
                    if (!n.contains("fds") && !n.contains("fispq") && !f.contains("fds") && !f.contains("fispq")) continue;
                } else if ("offline".equals(activeFilter)) {
                    if (!item.isCached() || item.isFolder()) continue;
                } else if ("folders".equals(activeFilter)) {
                    if (!item.isFolder()) continue;
                } else if ("pdf".equals(activeFilter)) {
                    if (item.isFolder()) continue;
                    String n = item.getName().toLowerCase();
                    if (!n.endsWith(".pdf") && !item.getMimeType().contains("pdf")) continue;
                } else if ("sheets".equals(activeFilter)) {
                    if (item.isFolder()) continue;
                    String n = item.getName().toLowerCase();
                    if (!n.endsWith(".xlsx") && !n.endsWith(".xls") && !n.endsWith(".csv")) continue;
                } else if ("images".equals(activeFilter)) {
                    if (item.isFolder()) continue;
                    String n = item.getName().toLowerCase();
                    if (!n.endsWith(".png") && !n.endsWith(".jpg") && !n.endsWith(".jpeg") && !n.endsWith(".svg")) continue;
                }

                if (item.isFolder()) {
                    int count = folderChildCount.getOrDefault(item.getId(), 0);
                    if (count > 0) {
                        item.setSubtitleInfo(count + (count == 1 ? " item" : " itens"));
                    } else {
                        item.setSubtitleInfo("Pasta do setor");
                    }
                } else {
                    item.setSubtitleInfo(isGlobalView ? extractShortSectorName(item) : "");
                }

                filtered.add(item);
            }

            Collections.sort(filtered, (o1, o2) -> {
                if (o1.isFolder() && !o2.isFolder()) return -1;
                if (!o1.isFolder() && o2.isFolder()) return 1;
                if (o1.isFavorite() != o2.isFavorite()) {
                    return o1.isFavorite() ? -1 : 1;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            });
        }

        adapter.setItems(filtered);
        txtItemsCount.setText(filtered.size() + (filtered.size() == 1 ? " item" : " itens"));

        if (filtered.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            if (!searchQuery.isEmpty()) {
                txtEmptyTitle.setText("Nenhum resultado encontrado");
                txtEmptyDesc.setText("Não há documentos para \"" + searchQuery + "\".");
            } else if ("favorites".equals(activeFilter)) {
                txtEmptyTitle.setText("Nada fixado ainda");
                txtEmptyDesc.setText("Toque na estrela de qualquer pasta ou documento para fixá-lo aqui.");
            } else if ("recents".equals(activeFilter)) {
                txtEmptyTitle.setText("Nenhum histórico recente");
                txtEmptyDesc.setText("Os documentos visualizados aparecerão aqui.");
            } else {
                txtEmptyTitle.setText("Nenhum documento listado");
                txtEmptyDesc.setText("Toque no cartão de Sincronização acima para atualizar.");
            }
        } else {
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    // ==========================================
    // INTERAÇÕES DA RECYCLERVIEW
    // ==========================================
    @Override
    public void onFolderClick(DriveItem folder) {
        boolean fromGlobalView = !"all".equals(activeFilter) || !searchQuery.isEmpty();
        if (!"all".equals(activeFilter)) {
            activeFilter = "all";
            updateBottomNavUi();
        }
        if (!searchQuery.isEmpty()) {
            editSearch.setText("");
            searchQuery = "";
        }
        if (fromGlobalView) {
            rebuildFolderStackFor(folder);
        } else {
            folderStack.push(new FolderCrumb(folder.getId(), folder.getName()));
        }
        updateBreadcrumb();
        applyFilterAndRender();
        recyclerDrive.scrollToPosition(0);
    }

    /** Reconstrói o caminho completo (Setores / ... / Pasta) ao abrir uma pasta a partir dos Fixados ou da busca. */
    private void rebuildFolderStackFor(DriveItem folder) {
        Map<String, DriveItem> byId = new HashMap<>();
        synchronized (allItems) {
            for (DriveItem it : allItems) byId.put(it.getId(), it);
        }
        LinkedList<FolderCrumb> chain = new LinkedList<>();
        DriveItem current = folder;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current.getId())) {
            chain.addFirst(new FolderCrumb(current.getId(), current.getName()));
            String pid = current.getParentId();
            if (pid == null || pid.isEmpty() || "root".equals(pid)) break;
            current = byId.get(pid);
        }
        while (folderStack.size() > 1) folderStack.pop();
        if (folderStack.isEmpty()) folderStack.push(new FolderCrumb("root", "Setores"));
        for (FolderCrumb c : chain) folderStack.push(c);
    }

    @Override
    public void onFileClick(DriveItem file) {
        recordRecentFile(file.getId());
        if (file.isCached() && file.getLocalFile() != null && file.getLocalFile().exists()) {
            openFileWithNativeApp(file.getLocalFile(), file.getMimeType());
        } else {
            downloadAndOpenFile(file);
        }
    }

    @Override
    public void onFavoriteClick(DriveItem file) {
        String id = file.getId();
        if (favoriteIds.contains(id)) {
            favoriteIds.remove(id);
            file.setFavorite(false);
            Toast.makeText(this, file.isFolder() ? "Pasta removida dos Fixados" : "Documento removido dos Fixados", Toast.LENGTH_SHORT).show();
        } else {
            favoriteIds.add(id);
            file.setFavorite(true);
            Toast.makeText(this, file.isFolder() ? "Pasta fixada" : "Documento fixado", Toast.LENGTH_SHORT).show();
        }
        saveFavorites();
        applyFilterAndRender();
    }

    @Override
    public void onFileOptionsClick(DriveItem file) {
        showFileBottomSheet(file);
    }

    // ==========================================
    // ABERTURA DE ARQUIVO 100% NATIVA VIA INTENT
    // ==========================================
    private void openFileWithNativeApp(File file, String mimeType) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);

            String resolvedMime = mimeType;
            if (resolvedMime == null || resolvedMime.isEmpty() || resolvedMime.equals("application/octet-stream")) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".pdf")) resolvedMime = "application/pdf";
                else if (name.endsWith(".docx")) resolvedMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                else if (name.endsWith(".doc")) resolvedMime = "application/msword";
                else if (name.endsWith(".xlsx")) resolvedMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                else if (name.endsWith(".xls")) resolvedMime = "application/vnd.ms-excel";
                else if (name.endsWith(".pptx")) resolvedMime = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                else if (name.endsWith(".ppt")) resolvedMime = "application/vnd.ms-powerpoint";
                else if (name.endsWith(".png")) resolvedMime = "image/png";
                else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) resolvedMime = "image/jpeg";
                else if (name.endsWith(".csv") || name.endsWith(".txt")) resolvedMime = "text/plain";
                else if (name.endsWith(".mp4")) resolvedMime = "video/mp4";
                else resolvedMime = "*/*";
            }

            intent.setDataAndType(contentUri, resolvedMime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum leitor encontrado no aparelho para este formato.", Toast.LENGTH_LONG).show();
        }
    }

    private void downloadAndOpenFile(DriveItem item) {
        if (!isOnline()) {
            Toast.makeText(this, "Arquivo não disponível offline e sem conexão de rede.", Toast.LENGTH_LONG).show();
            return;
        }

        String serverUrl = getServerUrl();
        if (serverUrl.isEmpty()) {
            showServerSettingsDialog();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Baixando " + item.getName() + "...");
        progressDialog.setCancelable(false);
        if (!isFinishing() && !isDestroyed()) {
            progressDialog.show();
        }

        executor.execute(() -> {
            File storageDir = getDocsDir();
            File localFile = getLocalFile(storageDir, item.getId(), item.getName());
            File tempFile = new File(storageDir, localFile.getName() + ".part");

            try {
                URL dlUrl = new URL(serverUrl + "/api/files/" + item.getId() + "/download");
                HttpURLConnection conn = (HttpURLConnection) dlUrl.openConnection();
                conn.setRequestProperty(AUTH_TOKEN_HEADER, AUTH_TOKEN_VALUE);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(45000);

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    FileOutputStream out = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.flush();
                    out.close();
                    in.close();

                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (localFile.exists()) localFile.delete();
                        if (tempFile.renameTo(localFile)) {
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                    .putString("ver_" + item.getId(), item.getVersionKey()).commit();
                            item.setCached(true);
                            item.setLocalFile(localFile);

                            runOnUiThread(() -> {
                                try {
                                    if (progressDialog.isShowing() && !isFinishing() && !isDestroyed()) {
                                        progressDialog.dismiss();
                                    }
                                } catch (Exception ignored) {}
                                applyFilterAndRender();
                                openFileWithNativeApp(localFile, item.getMimeType());
                            });
                            return;
                        }
                    }
                }
                runOnUiThread(() -> {
                    try {
                        if (progressDialog.isShowing() && !isFinishing() && !isDestroyed()) {
                            progressDialog.dismiss();
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(MainActivity.this, "Erro ao baixar o arquivo.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    try {
                        if (progressDialog.isShowing() && !isFinishing() && !isDestroyed()) {
                            progressDialog.dismiss();
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(MainActivity.this, "Falha de conexão durante o download.", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });
    }

    // ==========================================
    // BOTTOM SHEET NATIVA DO ANDROID
    // ==========================================
    private void showFileBottomSheet(DriveItem file) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_file_actions, null);

        TextView txtName = sheetView.findViewById(R.id.sheet_txt_name);
        TextView txtMeta = sheetView.findViewById(R.id.sheet_txt_meta);
        TextView txtOfflineAction = sheetView.findViewById(R.id.sheet_txt_offline_action);

        txtName.setText(file.getName());
        String folderInfo = file.getFolderPath().isEmpty() ? "Raiz" : file.getFolderPath();
        txtMeta.setText(file.getSizeFormatted() + " • " + folderInfo + " • " + (file.isCached() ? "Disponível Offline" : "Na nuvem"));

        if (file.isCached()) {
            txtOfflineAction.setText("Remover Cópia Local");
        } else {
            txtOfflineAction.setText("Salvar no Aparelho (Offline)");
        }

        sheetView.findViewById(R.id.sheet_btn_open).setOnClickListener(v -> {
            dialog.dismiss();
            onFileClick(file);
        });

        sheetView.findViewById(R.id.sheet_btn_toggle_offline).setOnClickListener(v -> {
            dialog.dismiss();
            if (file.isCached() && file.getLocalFile() != null) {
                file.getLocalFile().delete();
                file.setCached(false);
                file.setLocalFile(null);
                Toast.makeText(MainActivity.this, "Cópia local removida.", Toast.LENGTH_SHORT).show();
                applyFilterAndRender();
            } else {
                downloadAndOpenFile(file);
            }
        });

        sheetView.findViewById(R.id.sheet_btn_share).setOnClickListener(v -> {
            dialog.dismiss();
            if (file.isCached() && file.getLocalFile() != null) {
                openFileWithNativeApp(file.getLocalFile(), file.getMimeType());
            } else {
                downloadAndOpenFile(file);
            }
        });

        dialog.setContentView(sheetView);
        dialog.show();
    }

    // ==========================================
    // DIALOG DE CONFIGURAÇÃO & SCANNER QR
    // ==========================================
    private void showServerSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_settings, null);

        Button btnScanQr = dialogView.findViewById(R.id.dialog_btn_scan_qr);
        EditText editUrl = dialogView.findViewById(R.id.dialog_edit_server_url);
        TextView txtStatus = dialogView.findViewById(R.id.dialog_txt_test_status);
        Button btnCancel = dialogView.findViewById(R.id.dialog_btn_cancel);
        Button btnTest = dialogView.findViewById(R.id.dialog_btn_test);
        Button btnSave = dialogView.findViewById(R.id.dialog_btn_save);

        editUrl.setText(getServerUrl());

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnScanQr.setOnClickListener(v -> {
            dialog.dismiss();
            ScanOptions options = new ScanOptions();
            options.setPrompt("Aponte a câmera para o QR Code do servidor");
            options.setBeepEnabled(true);
            options.setBarcodeImageEnabled(false);
            options.setOrientationLocked(false);
            barcodeLauncher.launch(options);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnTest.setOnClickListener(v -> {
            String testUrl = cleanServerUrl(editUrl.getText().toString());

            txtStatus.setVisibility(View.VISIBLE);
            txtStatus.setText("Verificando conexão...");
            txtStatus.setTextColor(0xFF717A71);

            final String finalUrl = testUrl;
            executor.execute(() -> {
                try {
                    URL u = new URL(finalUrl + "/api/status");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestProperty(AUTH_TOKEN_HEADER, AUTH_TOKEN_VALUE);
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    int code = c.getResponseCode();

                    runOnUiThread(() -> {
                        if (code == 200) {
                            txtStatus.setText("Conexão estabelecida com sucesso.");
                            txtStatus.setTextColor(0xFF2B3A2A);
                        } else {
                            txtStatus.setText("Servidor respondeu com código " + code);
                            txtStatus.setTextColor(0xFF993333);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        txtStatus.setText("Não foi possível conectar ao servidor informado.");
                        txtStatus.setTextColor(0xFF993333);
                    });
                }
            });
        });

        btnSave.setOnClickListener(v -> {
            String url = cleanServerUrl(editUrl.getText().toString());
            setServerUrl(url);
            dialog.dismiss();
            Toast.makeText(MainActivity.this, "Configuração salva.", Toast.LENGTH_SHORT).show();
            refreshDataFromServer();
        });

        dialog.show();
    }
}
