package m.hanet.forcegoundservice.forcegound;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import m.hanet.forcegoundservice.R;

public class CreateForegroundServiceActivity extends AppCompatActivity { // implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forceground_service);

        setTitle("dev2qa.com - Android Foreground Service Example.");

        Button startServiceButton = (Button)findViewById(R.id.start_foreground_service_button);
        startServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CreateForegroundServiceActivity.this, MyForeGroundService.class);
                intent.setAction(MyForeGroundService.ACTION_START_FOREGROUND_SERVICE);
                startService(intent);
            }
        });

        Button stopServiceButton = (Button)findViewById(R.id.stop_foreground_service_button);
        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CreateForegroundServiceActivity.this, MyForeGroundService.class);
                intent.setAction(MyForeGroundService.ACTION_STOP_FOREGROUND_SERVICE);
                startService(intent);
            }
        });
    }
}