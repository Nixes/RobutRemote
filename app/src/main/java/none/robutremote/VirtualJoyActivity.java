package none.robutremote;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.github.controlwear.virtual.joystick.android.JoystickView;

import static android.text.TextUtils.isEmpty;

public class VirtualJoyActivity extends AppCompatActivity {
    private class PingHost extends AsyncTask<String, Void, Boolean> {
        private Boolean name_valid = false;
        private String tmp_hostname;
        @Override
        protected Boolean doInBackground(String... hostnames) {
            tmp_hostname = hostnames[0];
            name_valid = false;
            try {
                name_valid = InetAddress.getByName(tmp_hostname).isReachable(500);
            } catch (IOException e) {
                // something
                name_valid = false;
                System.out.println("Exception validating robot hostname, e: " + e.toString());
            }
            return name_valid;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            // do something with the result
            if (name_valid) {
                hostname = tmp_hostname;
                System.out.println("Hostname was: "+ hostname);
            }
        }
    }

    private String hostname;

    private void sendMotorOutputs () {

    }

    private void calculateMotorOutputs(int angle, int strength) {
        int motor_a;
        int motor_b;

        if (angle > 0 & angle < 180) {
            // direction positive
        } else {
            // direction negative
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_joy);

        // hook up the virtual joystick
        JoystickView joystick = (JoystickView) findViewById(R.id.joystickView);
        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // don't process the input unless we've somewhere to send it
                if (!isEmpty(hostname)) {
                    calculateMotorOutputs(angle, strength);
                }
            }
        });
    }

    public void askIp (View view) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Servo Controller ip");
        alert.setMessage("Please enter the ip address of your servo controller.");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        //input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        input.append("192.168.1."); // put in a common lan ip range
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // ideally do some validation first

                String tmp_hostname = input.getText().toString();
                new PingHost().execute(tmp_hostname);


                TextView hostname_text_view = (TextView) findViewById(R.id.hostname);
                hostname_text_view.setText(hostname);
                //System.out.println("Hostname was: "+ hostname);
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });
        alert.show();
    }

}
