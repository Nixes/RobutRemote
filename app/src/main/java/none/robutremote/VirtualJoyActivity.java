package none.robutremote;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
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
                //savePreferences(); this breaks out of thread and causes a networkonmainthreadException
            }
        }
    }

    private class SendPacket extends AsyncTask<Integer, Void, Void> {
        int motor_a;
        int motor_b;

        // convert polar coords to cartesian which I understand more intuitively
        private Point polarToCart(int angle, int radius) {
            System.out.println(" polar angle: "+angle+" radius: "+radius);
            Point tmp_point = new Point();
            tmp_point.x = (int) (Math.cos(angle * Math.PI / 180) * radius);
            tmp_point.y = (int) (Math.sin(angle * Math.PI / 180) * radius);
            System.out.println(" cartesian X:" +tmp_point.x + " Y: " +tmp_point.y);
            return tmp_point;
        }

        // an implementation of the algo described here: http://www.impulseadventure.com/elec/robot-differential-steering.html
        private void calculateMotorOutputs(int angle, int strength) {
            Point cart_point = polarToCart(angle,strength);

            final double max_motor_speed = 1024.0;
            final double min_motor_speed = 600.0;

            final double max_joy_val = 100;

            final double fPivYLimit = 24.0; // 32.0 was originally recommended

            // TEMP VARIABLES
            double   nMotPremixL;    // Motor (left)  premixed output        (-100..+99)
            double   nMotPremixR;    // Motor (right) premixed output        (-100..+99)
            int     nPivSpeed;      // Pivot Speed                          (-100..+99)
            double   fPivScale;      // Balance scale b/w drive and pivot    (   0..1   )


            // Calculate Drive Turn output due to Joystick X input
            if (cart_point.y >= 0) {
                // Forward
                nMotPremixL = (cart_point.x>=0)? max_joy_val : (max_joy_val + cart_point.x);
                nMotPremixR = (cart_point.x>=0)? (max_joy_val - cart_point.x) : max_joy_val;
            } else {
                // Reverse
                nMotPremixL = (cart_point.x>=0)? (max_joy_val - cart_point.x) : max_joy_val;
                nMotPremixR = (cart_point.x>=0)? max_joy_val : (max_joy_val + cart_point.x);
            }

            // Scale Drive output due to Joystick Y input (throttle)
            nMotPremixL = nMotPremixL * cart_point.y/max_joy_val;
            nMotPremixR = nMotPremixR * cart_point.y/max_joy_val;

            // Now calculate pivot amount
            // - Strength of pivot (nPivSpeed) based on Joystick X input
            // - Blending of pivot vs drive (fPivScale) based on Joystick Y input
            nPivSpeed = cart_point.x;
            fPivScale = (Math.abs(cart_point.y)>fPivYLimit)? 0.0 : (1.0 - Math.abs(cart_point.y)/fPivYLimit);

            // Calculate final mix of Drive and Pivot, produces normalised values between -1 and 1
            double motor_a_prescale = ( (1.0-fPivScale)*nMotPremixL + fPivScale*( nPivSpeed) ) /100;
            double motor_b_prescale = ( (1.0-fPivScale)*nMotPremixR + fPivScale*(-nPivSpeed) ) /100;

            // convert normalised values to usable motor range
            motor_a = (int)( motor_a_prescale * (max_motor_speed - min_motor_speed) + (Math.signum(motor_a_prescale)*min_motor_speed) );
            motor_b = (int)( motor_b_prescale * (max_motor_speed - min_motor_speed) + (Math.signum(motor_b_prescale)*min_motor_speed) );

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

            String ascii_packet = '[' + Integer.toString(motor_a) + ',' + Integer.toString(motor_b) + ']';
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

    protected void savePreferences() {
        String tmp_hostname = hostname.getHostName();
        System.out.println("stringified hostname was: " + tmp_hostname);
        if (tmp_hostname != null) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("last_hostname", tmp_hostname);
            editor.commit();
        }
    }

    protected void loadPreferences() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String saved_hostname = settings.getString("last_hostname", null);
        if (saved_hostname != null) {
            new PingHost().execute(saved_hostname);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_joy);

        loadPreferences();

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
