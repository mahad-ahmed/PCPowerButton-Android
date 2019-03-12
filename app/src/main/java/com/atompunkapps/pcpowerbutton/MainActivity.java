package com.atompunkapps.pcpowerbutton;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private static final int CMD_PORT = 6943;
    private static final int STATUS_PORT = 6947;

    private static final byte START = 100;
    private static final byte RESTART = 101;
    private static final byte FORCED_SHUTDOWN = 102;
    private static final byte CHECK_STATUS = 103;

    private DatagramSocket socket;

    private CheckedTextView statusView;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            //noinspection deprecation
            if(bundle != null && ConnectivityManager.TYPE_WIFI == bundle.getInt("networkType", 0)) {
                checkStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.text_status);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final DatagramPacket packet = new DatagramPacket(new byte[1], 1);
                final DatagramSocket statusSocket;
                try {
                    statusSocket = new DatagramSocket(STATUS_PORT);
                }
                catch (SocketException sex) {
                    return;
                }
                //noinspection InfiniteLoopStatement
                while(true) {
                    try {
                        statusSocket.receive(packet);
                        final boolean status = packet.getData()[0] == 1;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setStatus(status);
                            }
                        });
                    }
                    catch (IOException ignored) {}
                }
            }
        }).start();

        final InetAddress broadcastAddress;

        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);

            broadcastAddress = InetAddress.getByName("255.255.255.255");
        }
        catch (SocketException | UnknownHostException ex) {
            return;
        }

        final DatagramPacket START_PACKET = new DatagramPacket(new byte[]{START}, 1, broadcastAddress, CMD_PORT);
        final DatagramPacket RESTART_PACKET = new DatagramPacket(new byte[]{RESTART}, 1, broadcastAddress, CMD_PORT);
        final DatagramPacket FORCED_SHUTDOWN_PACKET = new DatagramPacket(new byte[]{FORCED_SHUTDOWN}, 1, broadcastAddress, CMD_PORT);

        findViewById(R.id.button_power).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                sendCommandAsync(START_PACKET, view);
            }
        });

        findViewById(R.id.button_restart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendCommandAsync(RESTART_PACKET, view);
            }
        });

        findViewById(R.id.button_force_shutdown).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendCommandAsync(FORCED_SHUTDOWN_PACKET, view);
            }
        });

        findViewById(R.id.status_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkStatus();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //noinspection deprecation
        registerReceiver(broadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    private void sendCommandAsync(final DatagramPacket packet, final View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.send(packet);
                }
                catch (IOException e) {
                    if(view != null) {
                        animateButton(view);
                    }
                }
            }
        }).start();
    }

    private void checkStatus() {
        statusView.setVisibility(View.GONE);
        try {
            sendCommandAsync(
                    new DatagramPacket(new byte[]{CHECK_STATUS}, 1, InetAddress.getByName("255.255.255.255"), CMD_PORT),
                    null
            );
        }
        catch (UnknownHostException ex) {
            ex.printStackTrace();

        }
    }

    @SuppressLint("SetTextI18n")
    private void setStatus(boolean status) {
        if(status) {
            statusView.setText("Powered ON");
        }
        else {
            statusView.setText("Powered OFF");
        }
        statusView.setChecked(status);
        statusView.setVisibility(View.VISIBLE);
    }

    private void animateButton(View view) {
        final ObjectAnimator animator = ObjectAnimator.ofInt(
                view,
                "backgroundColor",
                Color.parseColor("#DD0000"),
                Color.parseColor("#DDDDDD")
        );
        animator.setEvaluator(new ArgbEvaluator());
        animator.setDuration(900);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                animator.start();
            }
        });
    }
}
