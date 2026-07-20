package com.pixeldrift.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Configuration screen for the Pixel Drift live wallpaper. */
public final class MainActivity extends Activity
        implements AssetWorkQueue.Listener {
    private static final int REQUEST_IMPORT_DOCUMENT = 1001;
    private static final String STATE_GRID_EDITED = "grid_edited";
    private static final int[] FPS_VALUES = {0, 4, 6, 8, 12, 16, 24};
    private static final String[] SCALE_VALUES = {"fit", "fill", "stretch"};

    private WallpaperPreferences preferences;
    private AssetStore assetStore;
    private TextView sourceStatus;
    private ProgressBar progress;
    private Button importButton;
    private Button demoButton;
    private Button saveButton;
    private Button previewButton;
    private EditText columnsInput;
    private EditText rowsInput;
    private EditText frameCountInput;
    private EditText backgroundColorInput;
    private Spinner fpsSpinner;
    private Spinner scaleSpinner;
    private Switch batterySaverSwitch;
    private boolean updatingGridFields;
    private boolean gridEditedByUser;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = new WallpaperPreferences(this);
        assetStore = new AssetStore(this);
        bindViews();
        restorePreferences();
        attachGridWatchers();
        gridEditedByUser = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_GRID_EDITED, false);
        refreshSourceStatus();

        importButton.setOnClickListener(view -> openDocumentPicker());
        demoButton.setOnClickListener(view -> createDemoAsync());
        saveButton.setOnClickListener(view -> saveConfiguration(true));
        previewButton.setOnClickListener(view -> previewWallpaper());
    }

    private void bindViews() {
        sourceStatus = findViewById(R.id.source_status);
        progress = findViewById(R.id.progress);
        importButton = findViewById(R.id.import_button);
        demoButton = findViewById(R.id.demo_button);
        saveButton = findViewById(R.id.save_button);
        previewButton = findViewById(R.id.preview_button);
        columnsInput = findViewById(R.id.columns_input);
        rowsInput = findViewById(R.id.rows_input);
        frameCountInput = findViewById(R.id.frame_count_input);
        backgroundColorInput = findViewById(R.id.background_color_input);
        fpsSpinner = findViewById(R.id.fps_spinner);
        scaleSpinner = findViewById(R.id.scale_spinner);
        batterySaverSwitch = findViewById(R.id.battery_saver_switch);
    }

    private void restorePreferences() {
        WallpaperConfig current = preferences.load();
        columnsInput.setText(String.valueOf(current.getColumns()));
        rowsInput.setText(String.valueOf(current.getRows()));
        frameCountInput.setText(String.valueOf(current.getFrameCount()));
        fpsSpinner.setSelection(indexOf(FPS_VALUES, current.getFps(), 3));
        scaleSpinner.setSelection(indexOf(SCALE_VALUES, current.getScaleMode(), 0));
        backgroundColorInput.setText(String.format(
                Locale.US,
                "#%06X",
                current.getBackgroundColor() & 0x00FFFFFF));
        batterySaverSwitch.setChecked(current.freezesInBatterySaver());
    }

    private void refreshSourceStatus() {
        String status = assetStore.getSourceStatus();
        if (status == null || status.trim().isEmpty()) {
            sourceStatus.setText(R.string.source_status_empty);
            return;
        }
        sourceStatus.setText(getString(R.string.source_status_ready, status));
    }

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/webp"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.import_picker_title)),
                    REQUEST_IMPORT_DOCUMENT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.document_picker_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_DOCUMENT
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        GridInput grid = readGridInput();
        if (grid != null) {
            importDocumentAsync(uri, grid);
        }
    }

    private void importDocumentAsync(Uri uri, GridInput grid) {
        boolean preserveExistingGrid = assetStore.isCustomSelected() || gridEditedByUser;
        long operationToken = AssetWorkQueue.beginOperation(AssetWorkQueue.OperationType.IMPORT);
        Context appContext = getApplicationContext();
        AssetStore operationStore = new AssetStore(appContext);
        AssetWorkQueue.execute(() -> {
            try {
                AssetImporter.importDocument(
                        appContext,
                        uri,
                        operationStore,
                        grid.columns,
                        grid.rows,
                        grid.frameCount,
                        preserveExistingGrid,
                        operationToken);
                AssetWorkQueue.completeSuccess(operationToken);
            } catch (Exception | OutOfMemoryError error) {
                AssetWorkQueue.completeFailure(operationToken, error.getLocalizedMessage());
            }
        });
    }

    private void createDemoAsync() {
        long operationToken = AssetWorkQueue.beginOperation(AssetWorkQueue.OperationType.DEMO);
        Context appContext = getApplicationContext();
        AssetStore operationStore = new AssetStore(appContext);
        AssetWorkQueue.execute(() -> {
            try {
                operationStore.createDemoAsset(operationToken);
                AssetWorkQueue.completeSuccess(operationToken);
            } catch (Exception | OutOfMemoryError error) {
                AssetWorkQueue.completeFailure(operationToken, error.getLocalizedMessage());
            }
        });
    }

    private void attachGridWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!updatingGridFields) {
                    gridEditedByUser = true;
                }
            }
        };
        columnsInput.addTextChangedListener(watcher);
        rowsInput.addTextChangedListener(watcher);
        frameCountInput.addTextChangedListener(watcher);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        importButton.setEnabled(!busy);
        demoButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        previewButton.setEnabled(!busy);
        columnsInput.setEnabled(!busy);
        rowsInput.setEnabled(!busy);
        frameCountInput.setEnabled(!busy);
        backgroundColorInput.setEnabled(!busy);
        fpsSpinner.setEnabled(!busy);
        scaleSpinner.setEnabled(!busy);
        batterySaverSwitch.setEnabled(!busy);
    }

    private boolean saveConfiguration(boolean showConfirmation) {
        GridInput grid = readGridInput();
        if (grid == null) {
            return false;
        }

        String colorText = backgroundColorInput.getText().toString().trim();
        if (!colorText.matches("#[0-9A-Fa-f]{6}")) {
            backgroundColorInput.setError(getString(R.string.invalid_color));
            backgroundColorInput.requestFocus();
            return false;
        }

        int color = Color.parseColor(colorText);
        int fps = FPS_VALUES[fpsSpinner.getSelectedItemPosition()];
        String scaleMode = SCALE_VALUES[scaleSpinner.getSelectedItemPosition()];
        try {
            boolean saved = preferences.save(
                    grid.columns,
                    grid.rows,
                    grid.frameCount,
                    fps,
                    scaleMode,
                    color,
                    batterySaverSwitch.isChecked());
            if (!saved) {
                Toast.makeText(this, R.string.settings_write_failed, Toast.LENGTH_LONG).show();
                return false;
            }
            gridEditedByUser = false;
        } catch (IllegalArgumentException error) {
            String detail = error.getLocalizedMessage();
            if (detail == null || detail.trim().isEmpty()) {
                detail = getString(R.string.unknown_error);
            }
            Toast.makeText(
                    this,
                    getString(R.string.settings_rejected, detail),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (showConfirmation) {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private GridInput readGridInput() {
        Integer columns = readPositiveNumber(columnsInput, 1, 64);
        Integer rows = readPositiveNumber(rowsInput, 1, 64);
        Integer frameCount = readPositiveNumber(frameCountInput, 1, 256);
        if (columns == null || rows == null || frameCount == null) {
            return null;
        }
        if ((long) columns * rows > 256L) {
            rowsInput.setError(getString(R.string.invalid_grid_cells));
            rowsInput.requestFocus();
            return null;
        }
        if (frameCount > columns * rows) {
            frameCountInput.setError(getString(R.string.invalid_frame_count));
            frameCountInput.requestFocus();
            return null;
        }
        return new GridInput(columns, rows, frameCount);
    }

    private Integer readPositiveNumber(EditText input, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value >= minimum && value <= maximum) {
                input.setError(null);
                return value;
            }
        } catch (NumberFormatException ignored) {
            // The same concise validation message covers empty and malformed input.
        }
        input.setError(getString(R.string.invalid_number, minimum, maximum));
        input.requestFocus();
        return null;
    }

    private void previewWallpaper() {
        if (!saveConfiguration(false)) {
            return;
        }

        ComponentName component = new ComponentName(this, PixelWallpaperService.class);
        Intent preview = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        preview.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
        try {
            startActivity(preview);
        } catch (ActivityNotFoundException firstError) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException secondError) {
                Toast.makeText(
                        this,
                        R.string.wallpaper_picker_unavailable,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static int indexOf(int[] values, int target, int fallback) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == target) {
                return index;
            }
        }
        return fallback;
    }

    private static int indexOf(String[] values, String target, int fallback) {
        if (target != null) {
            for (int index = 0; index < values.length; index++) {
                if (values[index].equals(target)) {
                    return index;
                }
            }
        }
        return fallback;
    }

    @Override
    protected void onStart() {
        super.onStart();
        AssetWorkQueue.addListener(this);
        refreshSourceStatus();
        applyAssetWorkState();
    }

    @Override
    protected void onStop() {
        AssetWorkQueue.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        applyAssetWorkState();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_GRID_EDITED, gridEditedByUser);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        updatingGridFields = true;
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } finally {
            updatingGridFields = false;
            gridEditedByUser = savedInstanceState.getBoolean(STATE_GRID_EDITED, false);
        }
    }

    @Override
    public void onAssetWorkStateChanged() {
        runOnUiThread(this::applyAssetWorkState);
    }

    private void applyAssetWorkState() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        AssetWorkQueue.Snapshot snapshot = AssetWorkQueue.snapshot();
        setBusy(snapshot.isRunning());
        if (!resumed || !snapshot.hasResult()) {
            return;
        }

        AssetWorkQueue.Snapshot result = AssetWorkQueue.consumeResult();
        if (!result.hasResult()) {
            return;
        }
        if (result.isSuccess()) {
            refreshGridFromPreferences();
            refreshSourceStatus();
            int message = result.getType() == AssetWorkQueue.OperationType.DEMO
                    ? R.string.demo_ready
                    : R.string.import_complete;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        int message = result.getType() == AssetWorkQueue.OperationType.DEMO
                ? R.string.demo_failed
                : R.string.import_failed;
        Toast.makeText(
                this,
                getString(message, result.getFailureDetail()),
                Toast.LENGTH_LONG
        ).show();
    }

    private void refreshGridFromPreferences() {
        WallpaperConfig current = preferences.load();
        updatingGridFields = true;
        try {
            columnsInput.setText(String.valueOf(current.getColumns()));
            rowsInput.setText(String.valueOf(current.getRows()));
            frameCountInput.setText(String.valueOf(current.getFrameCount()));
            gridEditedByUser = false;
        } finally {
            updatingGridFields = false;
        }
    }

    private static final class GridInput {
        private final int columns;
        private final int rows;
        private final int frameCount;

        private GridInput(int columns, int rows, int frameCount) {
            this.columns = columns;
            this.rows = rows;
            this.frameCount = frameCount;
        }
    }
}
