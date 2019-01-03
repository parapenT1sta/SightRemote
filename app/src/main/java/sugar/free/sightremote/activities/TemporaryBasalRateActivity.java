package sugar.free.sightremote.activities;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Toast;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import sugar.free.sightparser.applayer.descriptors.PumpStatus;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ConfigurationBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.TBROverNotificationBlock;
import sugar.free.sightparser.applayer.messages.configuration.ReadConfigurationBlockMessage;
import sugar.free.sightparser.applayer.messages.configuration.WriteConfigurationBlockMessage;
import sugar.free.sightparser.applayer.messages.status.PumpStatusMessage;
import sugar.free.sightparser.handling.SingleMessageTaskRunner;
import sugar.free.sightparser.handling.TaskRunner;
import sugar.free.sightparser.handling.taskrunners.WriteConfigurationTaskRunner;
import sugar.free.sightremote.taskrunners.SetTBRTaskRunner;
import sugar.free.sightparser.pipeline.Status;
import sugar.free.sightremote.R;
import sugar.free.sightremote.dialogs.ConfirmationDialog;
import sugar.free.sightremote.utils.DurationPicker;
import sugar.free.sightremote.utils.HTMLUtil;
import sugar.free.sightremote.utils.UnitFormatter;

public class TemporaryBasalRateActivity extends SightActivity implements View.OnClickListener, TaskRunner.ResultCallback, NumberPicker.OnValueChangeListener, DurationPicker.OnDurationChangeListener {

    private TBROverNotificationBlock configurationBlock;
    private MenuItem enableItem;
    private MenuItem disableItem;

    private DurationPicker durationPicker;
    private ConfirmationDialog confirmationDialog;

    private NumberPicker percentage;
    private NumberPicker digit1;
    private NumberPicker digit2;
    private Button setTBR;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContent(R.layout.activity_temporary_basal_rate);

        showManualOverlay();

        percentage = findViewById(R.id.percentage);
        percentage.setOnValueChangedListener(this);
        percentage.setMinValue(0);
        percentage.setMaxValue(25);
        percentage.setValue(10);
        percentage.setDisplayedValues(new String[] {"0%", "10%", "20%", "30%", "40%", "50%", "60%", "70%", "80%",
                                                    "90%", "100%", "110%", "120%", "130%", "140%", "150%", "160%", "170%",
                                                    "180%", "190%", "200%", "210%", "220%", "230%", "240%", "250%"});

        digit1 = findViewById(R.id.digit1);
        digit2 = findViewById(R.id.digit2);

        setTBR = findViewById(R.id.set_tbr);
        setTBR.setOnClickListener(this);
        setTBR.setEnabled(false);

        durationPicker = new DurationPicker(digit1, digit2);
        durationPicker.setOnDurationChangeListener(this);
    }

    @Override
    protected void connectedToService() {
        getServiceConnector().connect();
        statusChanged(getServiceConnector().getStatus());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tbr_menu, menu);
        enableItem = menu.findItem(R.id.enable_tbr_over_notification);
        disableItem = menu.findItem(R.id.disable_tbr_over_notification);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        item.setVisible(false);
        configurationBlock.setEnabled(item == enableItem);
        new WriteConfigurationTaskRunner(getServiceConnector(), new ArrayList<>(Collections.singletonList(configurationBlock))).fetch(new TaskRunner.ResultCallback() {
            @Override
            public void onResult(Object result) {
                runOnUiThread(() -> {
                    Toast.makeText(TemporaryBasalRateActivity.this, R.string.changed_tbr_over_notification, Toast.LENGTH_SHORT).show();
                    if (configurationBlock.isEnabled()) {
                        enableItem.setVisible(false);
                        disableItem.setVisible(true);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(TemporaryBasalRateActivity.this, getString(R.string.error, e.getClass().getSimpleName()), Toast.LENGTH_SHORT).show());
            }
        });
        return true;
    }

    @Override
    protected void statusChanged(Status status) {
        if (status == Status.CONNECTED) {
            new SingleMessageTaskRunner(getServiceConnector(), new PumpStatusMessage()).fetch(this);
            showLoadingIndicator();
            hideManualOverlay();
        } else {
            if (confirmationDialog != null) confirmationDialog.hide();
            showManualOverlay();
            hideLoadingIndicator();
            disableItem.setVisible(false);
            enableItem.setVisible(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (confirmationDialog != null) confirmationDialog.hide();
    }

    @Override
    public void onClick(View v) {
        int duration = durationPicker.getPickerValue();
        int amount = percentage.getValue() * 10;
        SetTBRTaskRunner taskRunner = new SetTBRTaskRunner(getServiceConnector(), amount, duration);
        (confirmationDialog = new ConfirmationDialog(this, HTMLUtil.getHTML(R.string.tbr_confirmation, amount, UnitFormatter.formatDuration(duration)),
                () -> {
                    showManualOverlay();
                    showLoadingIndicator();
                    taskRunner.fetch(TemporaryBasalRateActivity.this);
                })).show();
    }

    @Override
    public void onResult(Object result) {
        if (result instanceof PumpStatusMessage) {
            PumpStatusMessage pumpStatusMessage = (PumpStatusMessage) result;
            if (pumpStatusMessage.getPumpStatus() != PumpStatus.STARTED) {
                showManualOverlay();
                hideLoadingIndicator();
                showSnackbar(Snackbar.make(getRootView(), R.string.pump_not_started, Snackbar.LENGTH_INDEFINITE));
            } else {
                hideLoadingIndicator();
                hideManualOverlay();
                dismissSnackbar();
            }
            ReadConfigurationBlockMessage readMessage = new ReadConfigurationBlockMessage();
            readMessage.setConfigurationBlockID(TBROverNotificationBlock.ID);
            new SingleMessageTaskRunner(getServiceConnector(), readMessage).fetch(this);
        } else if (result instanceof ReadConfigurationBlockMessage) {
            ReadConfigurationBlockMessage readMessage = (ReadConfigurationBlockMessage) result;
            configurationBlock = (TBROverNotificationBlock) readMessage.getConfigurationBlock();
            runOnUiThread(() -> {
                if (configurationBlock.isEnabled()) {
                    enableItem.setVisible(false);
                    disableItem.setVisible(true);
                } else {
                    enableItem.setVisible(true);
                    disableItem.setVisible(false);
                }
            });
        } else {
            Answers.getInstance().logCustom(new CustomEvent("TBR Programmed"));
            finish();
        }
    }

    @Override
    public void onError(Exception e) {
        runOnUiThread(() -> Toast.makeText(this, getString(R.string.error, e.getClass().getSimpleName()), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        setTBR.setEnabled(newVal != 10 && durationPicker.getPickerValue() > 0);
    }

    @Override
    public void onDurationChange(int newValue) {
        setTBR.setEnabled(percentage.getValue() != 10 && newValue > 0);
    }

    @Override
    protected int getSelectedNavItemID() {
        return R.id.nav_tbr;
    }
}
