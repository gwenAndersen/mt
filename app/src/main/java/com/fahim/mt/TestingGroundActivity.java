package com.fahim.mt;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fahim.mt.mtproto.TelegramClientManager;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class TestingGroundActivity extends AppCompatActivity {

    private TextView logTextView;
    private MaterialButton interactButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing_ground);

        logTextView = findViewById(R.id.testingLogTextView);
        interactButton = findViewById(R.id.interactGroupButton);
        final String defaultGroupId = "-1002358473714";

        interactButton.setOnClickListener(v -> {
            logTextView.setText("Fetching messages from CentStock Private...");
            TelegramClientManager.getInstance(this).fetchRecentMessages(defaultGroupId, 20, new TelegramClientManager.MessagesCallback() {
                @Override
                public void onMessagesReceived(List<String> messages) {
                    runOnUiThread(() -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < messages.size(); i++) {
                            sb.append(messages.get(i)).append("\n")
                              .append("-------------------\n");
                        }
                        logTextView.setText(sb.toString());
                        Toast.makeText(TestingGroundActivity.this, "Fetched 20 messages", Toast.LENGTH_SHORT).show();
                    });
                    
                    // Also trigger the standard bot interaction logic
                    TelegramClientManager.getInstance(TestingGroundActivity.this).interactWithBot(defaultGroupId);
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        logTextView.setText("Error: " + error);
                        Toast.makeText(TestingGroundActivity.this, "Failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }
}
