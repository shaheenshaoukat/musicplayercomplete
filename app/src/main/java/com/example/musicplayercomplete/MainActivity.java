package com.example.musicplayercomplete;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jean.jcplayer.model.JcAudio;
import com.example.jean.jcplayer.view.JcPlayerView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private boolean checkPermission=false;
    Uri uri;

    String songName,songUrl;
    ListView listView;
    ArrayList<String> arraylistsongName=new ArrayList<>();
    ArrayList<String> arraylistsongUrl=new ArrayList<>();
    ArrayAdapter<String> arrayAdapter;
    JcPlayerView jcPlayerView;
    ArrayList<JcAudio> jcAudios = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView=findViewById(R.id.myListView);
       jcPlayerView=findViewById(R.id.jcplayer);



        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                jcPlayerView.playAudio(jcAudios.get(i));
                jcPlayerView.setVisibility(View.VISIBLE);
                jcPlayerView.createNotification();

            }
        });

        retrieveSongs();
    }

    private void retrieveSongs() {


        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("Songs");

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                arraylistsongUrl.clear();
                arraylistsongName.clear();
                for(DataSnapshot ds :dataSnapshot.getChildren()){


                    Song songObj = ds.getValue(Song.class);
                    arraylistsongName.add(songObj.getSongName());
                    arraylistsongUrl.add(songObj.getSongUrl());
                jcAudios.add(JcAudio.createFromURL(songObj.getSongName(),songObj.getSongUrl()));



                }

                arrayAdapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,arraylistsongName){

                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

                        View view=super.getView(position,convertView,parent);
                        TextView textView=view.findViewById(android.R.id.text1);
                        textView.setSingleLine(true);
                        textView.setMaxLines(1);


                        return view;
                    }
                };
           jcPlayerView.initPlaylist(jcAudios,null);
                listView.setAdapter(arrayAdapter);




            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });





    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.custom_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if(item.getItemId()==R.id.nav_upload){

            if (validatePermission()){
                pickSong();
            }

           

        }

        return super.onOptionsItemSelected(item);
    }

    private void pickSong() {
        Intent intent_upload=new Intent();
        intent_upload.setType("audio/*");
        intent_upload.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent_upload,1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode==1){
            if (resultCode==RESULT_OK){
                uri=data.getData();
                Cursor mcursor=getApplicationContext().getContentResolver().query(uri,null,null,null,null);
                int indexname=mcursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                mcursor.moveToFirst();
                songName=mcursor.getString(indexname);
                mcursor.close();

                uploadsongtofirebasestorage();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void uploadsongtofirebasestorage() {

        StorageReference storageReference= FirebaseStorage.getInstance().getReference()
                .child("Song").child(uri.getLastPathSegment());


        ProgressDialog progressDialog=new ProgressDialog(this);
            progressDialog.show();

            storageReference.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                    Task<Uri> uriTask=taskSnapshot.getStorage().getDownloadUrl();
                    while (!uriTask.isComplete());
                    Uri urlsong=uriTask.getResult();
                    songUrl=urlsong.toString();

                    uploadDetailsToDatabase();
                    progressDialog.dismiss();

                }


            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MainActivity.this, e.getMessage().toString(), Toast.LENGTH_SHORT).show();
                    progressDialog.dismiss();

                }
            }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {
                    double progres=(100.0*snapshot.getBytesTransferred())/snapshot.getTotalByteCount();
                    int currentprogress=(int) progres;
                    progressDialog.setMessage("Upload:" + currentprogress+ "%");
                }
            });
    }

    private void uploadDetailsToDatabase() {
        Song songobj=new Song(songName,songUrl);
        FirebaseDatabase.getInstance().getReference("Songs")
                .push().setValue(songobj).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {

                        if (task.isSuccessful()){
                            Toast.makeText(MainActivity.this, "Song Uploaded", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, e.getMessage().toString(), Toast.LENGTH_SHORT).show();
                    }
                });


    }

    private boolean validatePermission() {

        Dexter.withActivity(MainActivity.this)
                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        checkPermission = true;
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        checkPermission = false;

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {

                        permissionToken.continuePermissionRequest();
                    }
                }).check();
        return checkPermission;
    }


    }