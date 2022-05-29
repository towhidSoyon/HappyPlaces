package com.example.happyplaces.activities


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.location.Address
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.example.happyplaces.R
import com.example.happyplaces.database.DatabaseHandler
import com.example.happyplaces.models.HappyPlaceModel
import com.example.happyplaces.utils.GetAddressFromLatLng
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_add_happy_place.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


class AddHappyPlaceActivity : AppCompatActivity() , View.OnClickListener{


    private var cal= Calendar.getInstance()
    private lateinit var dateSetListener: DatePickerDialog.OnDateSetListener
    private var saveImageToInternalStorage : Uri? = null
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0

    private var mHappyPlaceDetails : HappyPlaceModel? = null

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_happy_place)
        val toolbarAddPlace: Toolbar = findViewById(R.id.toolbarAddPlace)
        setSupportActionBar(toolbarAddPlace)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbarAddPlace.setNavigationOnClickListener {
            onBackPressed()
        }
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)



        if (!Places.isInitialized()){
            Places.initialize(this@AddHappyPlaceActivity,resources.getString(R.string.google_maps_api_key))
        }

        if(intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)){
            mHappyPlaceDetails = intent.getSerializableExtra(MainActivity.EXTRA_PLACE_DETAILS) as
                    HappyPlaceModel
        }

        dateSetListener = DatePickerDialog.OnDateSetListener{view,year,month,dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateInView()
        }
        updateDateInView()

        if(mHappyPlaceDetails != null){
            supportActionBar?.title = "Edit Happy Place"

            et_title.setText(mHappyPlaceDetails!!.title)
            et_description.setText(mHappyPlaceDetails!!.description)
            et_date.setText(mHappyPlaceDetails!!.date)
            et_location.setText(mHappyPlaceDetails!!.location)
            mLatitude = mHappyPlaceDetails!!.latitude
            mLongitude = mHappyPlaceDetails!!.longitude

            saveImageToInternalStorage = Uri.parse(mHappyPlaceDetails!!.image)

            iv_place_image.setImageURI(saveImageToInternalStorage)

            btn_save.text = "UPDATE"
        }

        et_date.setOnClickListener(this)
        tv_add_image.setOnClickListener(this)
        btn_save.setOnClickListener(this)
        et_location.setOnClickListener(this)
        currentLocation.setOnClickListener(this)

    }

    private fun isLocationEnabled(): Boolean{
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 1000
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            Log.e("Current Latitude", "$mLatitude")
            mLongitude = mLastLocation.longitude
            Log.e("Current Longitude", "$mLongitude")

            val addressTask =GetAddressFromLatLng(this@AddHappyPlaceActivity
            , mLatitude, mLongitude)

            addressTask.setAddressListener(object :GetAddressFromLatLng.AddressListener{
                override fun onAddressFound(address: String?){
                    et_location.setText(address)
                }

                override fun onError() {
                    TODO("Not yet implemented")
                }
            })
            addressTask.getAddress()
        }
    }

    override fun onClick(v: View?) {
       when(v!!.id){
           R.id.et_date ->{
               DatePickerDialog(this@AddHappyPlaceActivity,dateSetListener,cal.get(Calendar.YEAR),
                   cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()
           }

           R.id.tv_add_image ->{
               val pictureDialog = AlertDialog.Builder(this)
               pictureDialog.setTitle("Select Action")
               val  pictureDialogItems = arrayOf("Select photo from Gallery", "Capture photo from camera")
               pictureDialog.setItems(pictureDialogItems){
                   _, which ->
                   when(which){
                       0 -> choosePhotoFromGallery()
                       1 -> takePhotoFromCamera()
                   }
               }
               pictureDialog.show()
           }
           R.id.btn_save ->{
               when{
                   et_date.text.isNullOrEmpty() ->{
                       Toast.makeText(this,"Please enter title",Toast.LENGTH_SHORT).show()
                   }
                   et_description.text.isNullOrEmpty() ->{
                       Toast.makeText(this,"Please enter Description",Toast.LENGTH_SHORT).show()
                   }
                   et_location.text.isNullOrEmpty() ->{
                       Toast.makeText(this,"Please enter Location",Toast.LENGTH_SHORT).show()
                   }
                    saveImageToInternalStorage == null ->{
                        Toast.makeText(this,"Please select an image",Toast.LENGTH_SHORT).show()
                    } else -> {
                        val happyPlaceModel =HappyPlaceModel(
                            if(mHappyPlaceDetails == null) 0 else mHappyPlaceDetails!!.id,
                            et_title.text.toString(),
                            saveImageToInternalStorage.toString(),
                            et_description.text.toString(),
                            et_date.text.toString(),
                            et_location.text.toString(),
                            mLatitude,mLongitude
                        )
                   val dbHandler = DatabaseHandler(this)
                   if(mHappyPlaceDetails == null){
                       val addHappyPlace = dbHandler.addHappyPlace(happyPlaceModel)

                       if (addHappyPlace > 0){
                           setResult(Activity.RESULT_OK)
                           finish()
                       }
                   } else{
                       val updateHappyPlace = dbHandler.updateHappyPlace(happyPlaceModel)

                       if (updateHappyPlace > 0){
                           setResult(Activity.RESULT_OK)
                           finish()
                       }
                   }

                    }
               }
           }
           R.id.et_location ->  {
               try {
                   val fields = listOf(
                       Place.Field.ID, Place.Field.NAME,
                        Place.Field.LAT_LNG, Place.Field.ADDRESS
                   )

                   val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN,fields)
                       .build(this@AddHappyPlaceActivity)
                   startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)

               } catch (e:Exception){
                   e.printStackTrace()
               }
           }
           R.id.currentLocation -> {
               if (!isLocationEnabled()){
                   Toast.makeText(this,"Your Location is turned off. Please turn on",Toast.LENGTH_SHORT).show()

                   val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                   startActivity(intent)
               } else{
                   Dexter.withActivity(this).withPermissions(
                       Manifest.permission.ACCESS_FINE_LOCATION,
                       Manifest.permission.ACCESS_FINE_LOCATION
                   ).withListener(object : MultiplePermissionsListener{
                       override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                            if(report!!.areAllPermissionsGranted()){
                               requestNewLocationData()
                            }
                       }

                       override fun onPermissionRationaleShouldBeShown(
                           permissions: MutableList<PermissionRequest>?,
                           token: PermissionToken?
                       ) {
                            showRationalDialogForPermissions()
                       }
                   }).onSameThread().check()
               }
           }
       }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode==Activity.RESULT_OK){
            if (requestCode == GALLERY){
                if(data != null){
                    val contentUri = data.data
                    try{
                        val selectedImageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver,contentUri)
                        val iv_place_image: ImageView = findViewById(R.id.iv_place_image)

                        saveImageToInternalStorage = saveImageToInternalStorage(selectedImageBitmap)
                        iv_place_image.setImageBitmap(selectedImageBitmap)
                    } catch (e:IOException){
                        e.printStackTrace()
                        Toast.makeText(this@AddHappyPlaceActivity,"Failed to load the image from Gallery",Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (requestCode == CAMERA){
            val thumbnail : Bitmap = data!!.extras!!.get("data") as Bitmap
            saveImageToInternalStorage = saveImageToInternalStorage(thumbnail)
            val iv_place_image: ImageView = findViewById(R.id.iv_place_image)
            iv_place_image.setImageBitmap(thumbnail)

        } else if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE){
            val place: Place = Autocomplete.getPlaceFromIntent(data!!)
            et_location.setText(place.address)
            mLatitude = place.latLng!!.latitude
            mLongitude = place.latLng!!.longitude
        }
    }

    private fun takePhotoFromCamera(){
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(object: MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()){
                        val galleryIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivityForResult(galleryIntent, CAMERA)
                    }
                }
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) { showRationalDialogForPermissions()
                }
            }).onSameThread().check()
    }

    private fun choosePhotoFromGallery() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(object: MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()){
                        val galleryIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        startActivityForResult(galleryIntent, GALLERY)
                    }
                }
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) { showRationalDialogForPermissions()
                }
            }).onSameThread().check()
    }

    private fun showRationalDialogForPermissions() {
       AlertDialog.Builder(this).setMessage("Turned off permission")
           .setPositiveButton("GO TO SETTINGS"){
               _, _ ->
               try {
                   val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                   val uri = Uri.fromParts("package",packageName,null)
                   intent.data = uri
                   startActivity(intent)
               } catch (e:ActivityNotFoundException){
                   e.printStackTrace()
               }
           }.setNegativeButton("Cancel"){
               dialog, _ ->
               dialog.dismiss()
           }.show()
    }

    private fun updateDateInView(){
        val myFormat = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        var et_date: AppCompatEditText = findViewById(R.id.et_date)
        et_date.setText(sdf.format(cal.time).toString())
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap):Uri{
        val wrapper = ContextWrapper(applicationContext)
        var file = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        file = File(file,"${UUID.randomUUID()}.jpeg")

        try {
            val stream :OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,stream)
            stream.flush()
            stream.close()
        }catch (e:IOException){
            e.printStackTrace()
        }

        return Uri.parse(file.absolutePath)
    }

    companion object{
        private const val GALLERY = 1
        private const val CAMERA = 2
        private const val IMAGE_DIRECTORY = "HappyPlacesImages"
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 3
    }
}