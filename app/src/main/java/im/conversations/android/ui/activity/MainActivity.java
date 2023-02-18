package im.conversations.android.ui.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import im.conversations.android.service.ForegroundService;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ForegroundService.start(this);
    }
}
