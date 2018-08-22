package com.example.lulu.myapplication;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.lulu.myapplication.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1; //Activer le bluetooth

    BluetoothAdapter bluetoothAdapter;

    ArrayList<BluetoothDevice> pairedDeviceArrayList;

    TextView textInfo, textStatus;
    ListView listViewPairedDevice; //Liste appareils appariés
    RelativeLayout inputPane;
    EditText inputField;//Pas utilisé ici
    Button btnSend;//Pas utilisé ici
    Button m_play;//play et stop pour le son
    Button m_stop;
    TextToSpeech toSpeech; //On cree le text to speech
    int result; //Partie textToSpeech pour savoir si dispo sur tel ou pas
    String text; //text va prendre la valeur du buffer converti en string


    ArrayAdapter<BluetoothDevice> pairedDeviceAdapter;
    private UUID myUUID; //Adresse du telephone
    private final String UUID_STRING_WELL_KNOWN_SPP =
            "00001101-0000-1000-8000-00805F9B34FB";//Adresse de la carte

    ThreadBtconnect monThreadBtconnect; //Thread en arriere plan pour maintenir la connexion bluetooth
    ThreadConnected myThreadConnected; //Statut quand le thead est connecté on va pourvoir effectuer les actions necessaire à l'appli

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textInfo = (TextView)findViewById(R.id.info); //Lien entre interface et variables déclarées
        textStatus = (TextView)findViewById(R.id.status);
        listViewPairedDevice = (ListView)findViewById(R.id.pairedList);

        inputPane = (RelativeLayout)findViewById(R.id.inputpane); //Relative Layout à changer car lorsque la liste disparait, les autres élements bougent

        m_play=findViewById(R.id.m_play); //On affecte les valeurs aux boutons appli
        m_stop=findViewById(R.id.m_stop);

        //Creation du TextToSpeech
        toSpeech=new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS)
                {
                    result=toSpeech.setLanguage(Locale.FRANCE); //Configuration du text to speech
                }

                else
                {
                    Toast.makeText(getApplicationContext(),"Le text to speech n'est pas supporté sur votre télephone",Toast.LENGTH_SHORT).show();
                }

            }
        });

        //Verifier que Bluetooth bien supporté
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)){
            Toast.makeText(this,
                    "La fonctionnalité du Bluetooth n'est pas supporté",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        //using the well-known SPP UUID
        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this,
                    "Bluetooth non supporté",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String stInfo = bluetoothAdapter.getName() + "\n" +
                bluetoothAdapter.getAddress(); // Recupérer les adresses des appareils
        textInfo.setText(stInfo);
    }

    @Override
    protected void onStart() {
        super.onStart();

        //Activer le bluetooth s'il est sur OFF
        if (!bluetoothAdapter.isEnabled()) { //Si ce n'est pas activé on appelle un intent qui va demander autorisation
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); //Demande d'accès
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT); //REQUEST_ENABLE_BT = 1
        }

        setup();
    }

    private void setup() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices(); //Liste des appareils appriés
        if (pairedDevices.size() > 0) {
            pairedDeviceArrayList = new ArrayList<BluetoothDevice>();

            for (BluetoothDevice device : pairedDevices) {
                pairedDeviceArrayList.add(device); //On ajoute un appareil à chaque itération
            }

            pairedDeviceAdapter = new ArrayAdapter<BluetoothDevice>(this,
                    android.R.layout.simple_list_item_1, pairedDeviceArrayList);
            listViewPairedDevice.setAdapter(pairedDeviceAdapter);

            //Quand on clique sur un item alors activation de la connection Bluetooth
            listViewPairedDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view, //Quand n clique sur un item de la liste
                                        int position, long id) {
                    BluetoothDevice device =
                            (BluetoothDevice) parent.getItemAtPosition(position); //Recupere les donnees associees à la place specifique dans liste
                    Toast.makeText(MainActivity.this,
                            "Name: " + device.getName() + "\n"
                                    + "Address: " + device.getAddress() + "\n"
                                    + "BondState: " + device.getBondState() + "\n"
                                    + "BluetoothClass: " + device.getBluetoothClass() + "\n"
                                    + "Class: " + device.getClass(),
                            Toast.LENGTH_LONG).show();

                    textStatus.setText("début du ThreadBtconnect");
                    monThreadBtconnect = new ThreadBtconnect(device);
                    monThreadBtconnect.start();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(monThreadBtconnect!=null){
            monThreadBtconnect.cancel();
        }

        if(toSpeech!=null)
        {
            toSpeech.stop();
            toSpeech.shutdown();
        }
    } //Destructeur

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==REQUEST_ENABLE_BT){
            if(resultCode == Activity.RESULT_OK){
                setup();
            }else{
                Toast.makeText(this,
                        "BlueTooth NOT enabled",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    //Appelé dans ThreadBtconnect une fois que la connexion a été effectue avec succes
    //pour démarrer le ThreadConnected
    private void startThreadConnected(BluetoothSocket socket){

        myThreadConnected = new ThreadConnected(socket);
        myThreadConnected.start();
    }

    /*
    ThreadConnected:
    Thread pour maintenir l'echange d'info et les actions à réaliser pendant la connexion
     */
    private class ThreadConnected extends Thread {
        private final BluetoothSocket connectedBluetoothSocket;
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;

        public ThreadConnected(BluetoothSocket socket) {
            connectedBluetoothSocket = socket;
            InputStream in = null;
            OutputStream out = null;

            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            connectedInputStream = in;
            connectedOutputStream = out;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = connectedInputStream.read(buffer);
                    String strReceived = new String(buffer, 0, bytes);
                    final String msgReceived = String.valueOf(bytes) +
                            " bytes received:\n"
                            + strReceived;

                    runOnUiThread(new Runnable(){

                        @Override
                        public void run() {
                            textStatus.setText(msgReceived);
                        }});

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();

                    final String msgConnectionLost = "Connection lost:\n"
                            + e.getMessage();
                    runOnUiThread(new Runnable(){

                        @Override
                        public void run() {
                            textStatus.setText(msgConnectionLost);
                        }});
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                connectedBluetoothSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void TTS(View view)
    {
        switch (view.getId())
        {
            case R.id.m_play:
                if(result==TextToSpeech.LANG_MISSING_DATA || result==TextToSpeech.LANG_NOT_SUPPORTED)
                {
                    Toast.makeText(getApplicationContext(),"Le text to speech n'est pas supporté sur votre télephone",Toast.LENGTH_SHORT).show();
                }

                else
                {
                    text= textStatus.getText().toString(); //On recupere le texte de m_text que l'on affecte à text
                    toSpeech.speak(text, TextToSpeech.QUEUE_FLUSH,null); //On dit le ttexte recupere par text
                }
                break;

            case R.id.m_stop:
                if (toSpeech!=null)
                {
                    toSpeech.stop(); //Si on appuie sur stop alors il s'arrete de parler
                }
                break;
        }
    }

        /*
    ThreadBtconnect:
    Thread en arriere plan pour maintenir le Bluetooth connecte
    */

    private class ThreadBtconnect extends Thread {

        private BluetoothSocket bluetoothSocket = null;
        private final BluetoothDevice bluetoothDevice;


        private ThreadBtconnect(BluetoothDevice device) {
            bluetoothDevice = device;

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID); //Prend l'UUID donné et choisi quel canal de radio utiliser grâce au SDP
                textStatus.setText("bluetoothSocket: \n" + bluetoothSocket);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                bluetoothSocket.connect();
                success = true; //Changement du statut de succes
            } catch (IOException e) { //si exception, afficher erreur
                e.printStackTrace();

                final String eMessage = e.getMessage();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        textStatus.setText("il y a un probleme avec la fonction connect du bluetoothsocket: \n" + eMessage);
                    }
                });

                try {
                    bluetoothSocket.close();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }

            if(success){
                //connect successful
                final String msgconnected = "Connecté avec succès:\n"
                        + "BluetoothSocket: " + bluetoothSocket + "\n"
                        + "BluetoothDevice: " + bluetoothDevice;

                runOnUiThread(new Runnable(){

                    @Override
                    public void run() {
                        textStatus.setText(msgconnected);

                        listViewPairedDevice.setVisibility(View.GONE); //On rend la liste invisible
                        inputPane.setVisibility(View.VISIBLE);
                    }});

                startThreadConnected(bluetoothSocket);
            }else{
                //Erreur
            }
        }

        public void cancel() {

            Toast.makeText(getApplicationContext(),
                    "Fermer le bluetoothSocket",
                    Toast.LENGTH_LONG).show();

            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }


}
