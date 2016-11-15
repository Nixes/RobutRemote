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
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

import io.github.controlwear.virtual.joystick.android.JoystickView;

import static android.text.TextUtils.isEmpty;

public class VirtualJoyActivity extends AppCompatActivity {
    private class PingHost extends AsyncTask<String, Void, Boolean> {
        private Boolean name_valid = false;
        private InetAddress tmp_hostname;
        @Override
        protected Boolean doInBackground(String... hostnames) {
            System.out.println("Checking hostname is accessible");
            name_valid = false;
            try {
                tmp_hostname = InetAddress.getByName(hostnames[0]);
                name_valid = tmp_hostname.isReachable(500);
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
                // set the text element
                TextView hostname_text_view = (TextView) findViewById(R.id.hostname);
                hostname_text_view.setText("Host: "+ hostname);
            }
        }
    }

    private class SendPacket extends AsyncTask<Integer, Void, Void> {
        int motor_a;
        int motor_b;

        // convert polar coords to cartesian which I understand more intuitively
        private void polarToCart(int angle, int radius) {
            double x_pos = Math.cos(angle * Math.PI / 180) * radius;
            double y_pos = Math.sin(angle * Math.PI / 180) * radius;
            System.out.println(" cartesian X:" +x_pos + " Y: " +y_pos);
        }

        private void calculateMotorOutputs(int angle, int strength) {
            motor_a = strength * 10;
            motor_b = strength * 10;

            polarToCart(angle,strength);

            if (angle > 0 & angle < 180) {
                // direction positive
            } else {
                // direction negative
            }
        }

        @Override
        protected Void doInBackground(Integer... joypad_values) {
            if (udp_socket == null) {
                System.out.println("UDP Socket was not open, opening it now");
                try {
                    udp_socket = new DatagramSocket(3000);
                } catch (IOException exception) {
                    System.out.println("Unable to make a new UDP socket");
                    exception.printStackTrace();
                }
            }

            // do some processing to figure out motor values
            calculateMotorOutputs(joypad_values[0], joypad_values[1]);

            String ascii_packet = '[' + Integer.toString(motor_a) + ',' + Integer.toString(motor_b) + ']'; // this might need some work
            byte[] byte_packet = ascii_packet.getBytes(Charset.forName("UTF-8"));
            DatagramPacket packet = new DatagramPacket(byte_packet, byte_packet.length,hostname,3000);
            System.out.println(" Packet prepped: "+ascii_packet);

            try {
                udp_socket.send( packet );
            } catch (UnknownHostException exception) {
                System.out.println("UnknownHostException sending packet");
                exception.printStackTrace();
            } catch (IOException exception) {
                System.out.println("IOException sending packet");
                exception.printStackTrace();
            }

            return null;
        }
    }

    DatagramSocket udp_socket;

    private InetAddress hostname;

    private void sendMotorOutputs () {

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_joy);

        // hook up the virtual joystick
        JoystickView joystick = (JoystickView) findViewById(R.id.joystickView);
        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // set debug text outputs
                TextView text_view_angle = (TextView) findViewById(R.id.angle);
                text_view_angle.setText( "Angle: "+Integer.toString(angle) );

                TextView text_view_strength = (TextView) findViewById(R.id.strength);
                text_view_strength.setText( "Strength: "+Integer.toString(strength) );

                // don't process the input unless we've somewhere to send it
                if (hostname != null) {
                    new SendPacket().execute(angle,strength);
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
