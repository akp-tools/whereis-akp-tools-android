package tools.akp.whereis

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*


class LocationService : Service() {
    private val TAG: String = "LocationService"

    private lateinit var notification: Notification

    private var database: FirebaseDatabase = Firebase.database

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val binder = LocalBinder()

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of BackgroundLocationService so clients can call public methods
        fun getService(): LocationService = this@LocationService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "Service starting")
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                onLocation(locationResult.lastLocation)
            }
        }

        locationRequest = LocationRequest.create().apply {
            interval = 300000
            fastestInterval = 300000
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize
            // location requests here.
            startLocationUpdates()
        }

        val pendingIntent: PendingIntent =
            Intent(this, MapsActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notificationBuilder = Notification.Builder(this, "PERSISTENT")

        notification = notificationBuilder
            .setSmallIcon(R.drawable.ic_my_location)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(42, notification)

        val locationRef = database.getReference("location")

        val locationListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val locationName = dataSnapshot.child("name").value
                val updatedAt = dataSnapshot.child("updatedAt").value as? Long

                val date = updatedAt?.let { Date(it) }
                val dateFormat: java.text.DateFormat? = DateFormat.getTimeFormat(applicationContext)
                val dateText = date?.let { dateFormat?.format(it) }

                notificationBuilder.setContentTitle("$locationName")
                notificationBuilder.setContentText("Updated $dateText")
                updateNotification(notificationBuilder)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "loadLocation:onCancelled", databaseError.toException())
            }
        }

        locationRef.addValueEventListener(locationListener)
    }

    private fun updateNotification(notificationBuilder: Notification.Builder) {
        with(NotificationManagerCompat.from(this)) {
            Log.i(TAG, "updating notification")
            // notificationId is a unique int for each notification that you must define
            notify(42, notificationBuilder.build())
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel("PERSISTENT", name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    private fun onLocation(location: Location) {
        val locRef = database.getReference("newLocation")
        locRef.setValue(location).addOnCompleteListener {
            Log.i(TAG, "location updated")
        }.addOnFailureListener {
            Log.e(TAG, "location update failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
}
