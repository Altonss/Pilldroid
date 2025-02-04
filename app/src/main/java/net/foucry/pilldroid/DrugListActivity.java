package net.foucry.pilldroid;

import static net.foucry.pilldroid.UtilDate.date2String;
import static net.foucry.pilldroid.Utils.intRandomExclusive;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.zxing.client.android.BuildConfig;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanOptions;

import net.foucry.pilldroid.dao.MedicinesDAO;
import net.foucry.pilldroid.dao.PrescriptionsDAO;
import net.foucry.pilldroid.databases.MedicineDatabase;
import net.foucry.pilldroid.databases.PrescriptionDatabase;
import net.foucry.pilldroid.models.Medicine;
import net.foucry.pilldroid.models.Prescription;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * An activity representing a list of Drugs is activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link DrugDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class DrugListActivity extends AppCompatActivity {
    // Used for dev and debug
    final Boolean DEMO = false;

    public final int CUSTOMIZED_REQUEST_CODE = 0x0000ffff;
    public final String BARCODE_FORMAT_NAME = "Barcode Format name";
    public final String BARCODE_CONTENT = "Barcode Content";

    private ActivityResultLauncher<ScanOptions> mBarcodeScannerLauncher;
    private static final String TAG = DrugListActivity.class.getName();

    public PrescriptionDatabase prescriptions;
    public MedicineDatabase medicines;

    private List<Prescription> prescriptionList;         // used for prescriptions

    private SimpleItemRecyclerViewAdapter mAdapter;

    @Override
    public void onStart() {
        super.onStart();

        if(BuildConfig.DEBUG) {
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
            int version = Build.VERSION.SDK_INT;
            String versionRelease = Build.VERSION.RELEASE;

            Log.e(TAG, "manufacturer " + manufacturer
                    + " \n model " + model
                    + " \n version " + version
                    + " \n versionRelease " + versionRelease
            );
        }

        // Create medicines Room database from drugs.db files
        medicines = MedicineDatabase.getInstanceDatabase(this);

        // Create prescriptions Room database
        prescriptions = PrescriptionDatabase.getInstanceDatabase(this);

        // Manually migrate old database to room
        PrescriptionsDAO prescriptionsDAO = prescriptions.getPrescriptionsDAO();
        DBHelper dbHelper = new DBHelper(this);
        if (dbHelper.getCount() !=0) {
            List<Drug> drugs=dbHelper.getAllDrugs();
            for (int count=0; count < dbHelper.getCount(); count++) {
                Drug drug = drugs.get(count);
                Prescription prescription = new Prescription();

                if(prescriptionsDAO.getMedicByCIP13(drug.getCip13()) == null) {
                    prescription.setName(drug.getName());
                    prescription.setCip13(drug.getCip13());
                    prescription.setCis(drug.getCis());
                    prescription.setPresentation(drug.getPresentation());
                    prescription.setAdministration_mode(drug.getAdministration_mode());
                    prescription.setStock((float) drug.getStock());
                    prescription.setTake((float) drug.getTake());
                    prescription.setWarning(drug.getWarnThreshold());
                    prescription.setAlert(drug.getAlertThreshold());
                    prescription.setLast_update(drug.getDateLastUpdate());

                    prescriptionsDAO.insert(prescription);
                }
                else {
                    Log.i(TAG, "Already in the database");
                }
            }
            dbHelper.dropDrug();
        }
        // remove old notification
        Log.d(TAG, "Remove old notification and old job");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancelAll();
        }

        // start tutorial (only in non debug mode)
       // if(!net.foucry.pilldroid.BuildConfig.DEBUG) {
            Log.i(TAG, "Launch tutorial");
            startActivity(new Intent(this, WelcomeActivity.class));
       // }

        PrefManager prefManager = new PrefManager(this);
        if (!prefManager.isUnderstood()) {
            askForComprehensive();
            prefManager.setUnderstood(true);
        }


    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        if (!AlarmReceiver.isAlarmScheduled(this)){
            AlarmReceiver.scheduleAlarm(this);
        }

    }
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create Room database
        prescriptions = Room
                .databaseBuilder(getApplicationContext(), PrescriptionDatabase.class, "prescriptions")
                .allowMainThreadQueries()
                .build();

        // Set view content
        setContentView(R.layout.drug_list_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setTitle(getTitle());
        }

        if (DEMO) {
          PrescriptionsDAO prescriptionsDAO = prescriptions.getPrescriptionsDAO();

          if (prescriptionsDAO.getMedicCount() == 0) {
              final int min_stock = 5;
              final int max_stock = 50;
              final int min_take = 0;
              final int max_take = 3;

              for (int i = 1; i < 9; i++) {
                  Prescription prescription = new Prescription();
                  prescription.setName("Medicament test " + i);
                  prescription.setCip13("340093000001" + i);
                  prescription.setCis("6000001" + i);
                  prescription.setAdministration_mode("oral");
                  prescription.setPresentation("plaquette(s) thermoformée(s) PVC PVDC aluminium de 10 comprimé(s)");
                  prescription.setStock((float) intRandomExclusive(min_stock, max_stock));
                  prescription.setTake((float) intRandomExclusive(min_take, max_take));
                  prescription.setWarning(14);
                  prescription.setAlert(7);
                  prescription.setLast_update(UtilDate.dateAtNoon(new Date()).getTime());

                  prescriptionsDAO.insert(prescription);
              }
              List<Prescription> prescriptions = prescriptionsDAO.getAllMedics();
              System.out.println(prescriptions);
              Log.d(TAG, "prescriptions ==" + prescriptions);
          }
        }

        mBarcodeScannerLauncher = registerForActivityResult(new PilldroidScanContract(),
                result -> {
                    if (result.getContents() == null) {
                        Intent originalIntent = result.getOriginalIntent();
                        Bundle bundle = originalIntent.getExtras();
                        if (originalIntent.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION)) {
                            Log.d(TAG, "Missing camera permission");
                            Toast.makeText(this, R.string.missing_camera_permission, Toast.LENGTH_LONG).show();
                        } else {
                            Log.d(TAG, "bundle == " + bundle.getInt("returnCode"));
                            int returnCode = bundle.getInt("returnCode");
                            int resultCode = bundle.getInt("resultCode");

                            if (resultCode != 1) {
                                if (returnCode == 3) {
                                    if (BuildConfig.DEBUG) {
                                        Toast.makeText(this, "Keyboard input",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    Log.d(TAG, "Keyboard Input");
                                    showInputDialog();
                                } else if (returnCode == 2) {
                                    Toast.makeText(this, R.string.cancelled_scan, Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Log.d(TAG, "Scanned formatName = " + bundle.getString(BARCODE_FORMAT_NAME));
                                if (BuildConfig.DEBUG) {
                                    Toast.makeText(this, "Scanned: " + bundle.getString(BARCODE_FORMAT_NAME),
                                            Toast.LENGTH_LONG).show();
                                }

                                String cip13;
                                switch (bundle.getString(BARCODE_FORMAT_NAME)) {
                                    case "CODE_128":
                                    case "EAN_13":  //CODE_128 || EAN 13
                                        cip13 = bundle.getString(BARCODE_CONTENT);
                                        break;
                                    case "DATA_MATRIX":
                                        cip13 = bundle.getString(BARCODE_CONTENT).substring(4, 17);
                                        break;
                                    default:
                                        scanNotOK();
                                        return;
                                }

                                // Get Drug from database
                                MedicinesDAO medicinesDAO = medicines.getMedicinesDAO();
                                final Medicine scannedMedicine = medicinesDAO.getMedicineByCIP13(cip13);

                                // add Drug to prescription database
                                askToAddInDB(scannedMedicine);
                            }
                        }
                    }
                });
        constructDrugsList();
    }

    public void constructDrugsList() {

        PrescriptionsDAO prescriptionsDAO = prescriptions.getPrescriptionsDAO();
        prescriptionList = prescriptionsDAO.getAllMedics();

        View mRecyclerView = findViewById(R.id.drug_list);
        assert mRecyclerView != null;
        setupRecyclerView((RecyclerView) mRecyclerView);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.about, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.about) {
            startActivity(new Intent(this, About.class));
            return true;
        } else if (id == R.id.help) {
            PrefManager prefManager = new PrefManager(this);
            prefManager.setFirstTimeLaunch(true);

            startActivity(new Intent(this, WelcomeActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        constructDrugsList();
    }

    public void onResume() {
        super.onResume();
    }

    // Launch scan
    public void onButtonClick(View v) {
        Log.d(TAG, "add medication");
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.DATA_MATRIX, ScanOptions.CODE_128);
        options.setCameraId(0);  // Use a specific camera of the device
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(true);
        options.setTimeout(60);
        options.setCaptureActivity(CustomScannerActivity.class);
        options.setBeepEnabled(true);
        options.addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN);
        options.addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.INVERTED_SCAN);

        Log.d(TAG, "scanOptions == " +  options);
        mBarcodeScannerLauncher.launch(options);
    }

    /**
     * show keyboardInput dialog
     */
    protected void showInputDialog() {
        // get prompts.xml view
        LayoutInflater layoutInflater = LayoutInflater.from(DrugListActivity.this);
        View promptView = layoutInflater.inflate(R.layout.input_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DrugListActivity.this);
        alertDialogBuilder.setView(promptView);

        final EditText editText = promptView.findViewById(R.id.edittext);
        editText.setHint("1234567890123");
        // setup a dialog window

        alertDialogBuilder.setCancelable(false)
                .setPositiveButton("OK", (dialog, id) -> {
                    String cip13 = editText.getText().toString();

                    MedicinesDAO medicineDAO = medicines.getMedicinesDAO();
                    Medicine aMedicine = medicineDAO.getMedicineByCIP13(cip13);
                    askToAddInDB(aMedicine);
                })
                .setNegativeButton("Cancel",
                        (dialog, id) -> dialog.cancel());

        // create an alert dialog
        AlertDialog alert = alertDialogBuilder.create();

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                alert.getButton(alert.BUTTON_POSITIVE).setEnabled(s.length() == 13);
            }
        });
        alert.show();
    }

    /**
     * Ask if the drug found in the database should be include in the
     * user database
     *
     * @param aMedicine Prescription- medication to be added
     */
    private void askToAddInDB(Medicine aMedicine) {
        AlertDialog.Builder dlg = new AlertDialog.Builder(this);
        dlg.setTitle(getString(R.string.app_name));

        if (aMedicine != null) {
            String msg = aMedicine.getName() + " " + getString(R.string.msgFound);

            dlg.setMessage(msg);
            dlg.setNegativeButton(getString(R.string.button_cancel), (dialog, which) -> {
                // Nothing to do in case of cancel
            });
            dlg.setPositiveButton(getString(R.string.button_ok), (dialog, which) -> {
                // Add Drug to DB then try to show it
                addDrugToList(Utils.medicine2prescription(aMedicine));
            });
        } else {
            dlg.setMessage(getString(R.string.msgNotFound));
            dlg.setPositiveButton("OK", (dialog, which) -> {
                // nothing to do to just dismiss dialog
            });
        }
        dlg.show();
    }

    /**
     * Tell user that the barre code cannot be interpreted
     */
    private void scanNotOK() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(this);
        dlg.setTitle(getString(R.string.app_name));

        dlg.setMessage(R.string.notInterpreted);
        dlg.setPositiveButton("OK", (dialog, which) -> {
            // Nothing to do just dismiss dialog
        });
        dlg.show();
    }

    /**
     * askForCompréhensive
     */
    private void askForComprehensive() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(this);
        dlg.setTitle(getString(R.string.app_name));

        dlg.setMessage(R.string.understood);
        dlg.setPositiveButton(R.string.Yes, (dialog, which) -> {
            // Nothing to do just dismiss dialog
        });
        dlg.show();
    }


    /**
     * Add New drug to the user database
     *
     * @param aPrescription Prescription - medication to be added
     */

    @SuppressWarnings("deprecation")
    private void addDrugToList(Prescription aPrescription) {
        aPrescription.getDateEndOfStock();
        mAdapter.addItem(aPrescription);

        Log.d(TAG, "Call DrugDetailActivity");
        Context context = this;
        Intent intent = new Intent(context, DrugDetailActivity.class);
        intent.putExtra("prescription", aPrescription);

        startActivityForResult(intent, CUSTOMIZED_REQUEST_CODE);
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
    }
    /**
     * setupRecyclerView (list of drugs)
     *
     * @param recyclerView RecyclerView
     */
    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(getApplicationContext()));
        mAdapter = new SimpleItemRecyclerViewAdapter(prescriptionList);
        recyclerView.setAdapter(mAdapter);
    }

    /**
     * SimpleItemRecyclerViewAdapter
     */
    public class SimpleItemRecyclerViewAdapter extends
            RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final List<Prescription> mValues;

        SimpleItemRecyclerViewAdapter(List<Prescription> items) {
            mValues = items;
        }

        void addItem(Prescription scannedPrescription) {
            PrescriptionsDAO prescriptionsDAO = prescriptions.getPrescriptionsDAO();
            if (prescriptionsDAO.getMedicByCIP13(scannedPrescription.getCip13()) == null) {
                mValues.add(scannedPrescription);
                //notifyDataSetChanged();
                notifyItemInserted(mValues.size());
                prescriptionsDAO.insert(scannedPrescription);
            } else {
                Toast.makeText(getApplicationContext(), "already in the database", Toast.LENGTH_LONG).show();
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.drug_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onBindViewHolder(final ViewHolder holder, int dummy) {
            final int position = holder.getBindingAdapterPosition();
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault());
            String dateEndOfStock = date2String(mValues.get(position).getDateEndOfStock(), dateFormat);

            Log.d(TAG, "Drug name == " + mValues.get(position).getName());
            Log.d(TAG, "dateEndOfStock == " + dateEndOfStock);
            Log.d(TAG, "stock == " + mValues.get(position).getStock());
            Log.d(TAG, "take == " + mValues.get(position).getTake());
            Log.d(TAG, "warn == " + mValues.get(position).getWarnThreshold());
            Log.d(TAG, "alert == " + mValues.get(position).getAlertThreshold());

            holder.mItem = mValues.get(position);
            holder.mContentView.setText(mValues.get(position).getName());
            holder.mEndOfStock.setText(dateEndOfStock);


            // Test to change background programmatically
            if (mValues.get(position).getTake() == 0) {
                holder.mView.setBackgroundResource(R.drawable.gradient_bg);
                holder.mIconView.setImageResource(R.drawable.ic_suspended_pill);

                holder.mView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prescription aPrescription = mValues.get(position);
                        Context context = v.getContext();
                        Intent intent = new Intent(context, DrugDetailActivity.class);
                        intent.putExtra("prescription",  aPrescription);
                        startActivityForResult(intent, CUSTOMIZED_REQUEST_CODE);
                        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);

                    }
                });
            } else {
                int remainingStock = (int) Math.floor(mValues.get(position).getStock() / mValues.get(position).getTake());
                if (remainingStock <= mValues.get(position).getAlertThreshold()) {
                    holder.mView.setBackgroundResource(R.drawable.gradient_bg_alert);
                    holder.mIconView.setImageResource(R.drawable.lower_stock_vect);
                } else if ((remainingStock > mValues.get(position).getAlertThreshold()) &&
                        (remainingStock <= (mValues.get(position).getWarnThreshold()))) {
                    holder.mView.setBackgroundResource(R.drawable.gradient_bg_warning);
                    holder.mIconView.setImageResource(R.drawable.warning_stock_vect);
                } else {
                    holder.mView.setBackgroundResource(R.drawable.gradient_bg_ok);
                    holder.mIconView.setImageResource(R.drawable.ok_stock_vect);
                }

                holder.mView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prescription prescription = mValues.get(position);
                        Context context = v.getContext();
                        Intent intent = new Intent(context, DrugDetailActivity.class);
                        intent.putExtra("prescription", prescription);
                        startActivityForResult(intent, CUSTOMIZED_REQUEST_CODE);
                        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);

                    }
                });
            }

        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View mView;
            final TextView mContentView;
            final TextView mEndOfStock;
            final ImageView mIconView;
            public Prescription mItem;

            ViewHolder(View view) {
                super(view);
                mView = view;
                mContentView = view.findViewById(R.id.value);
                mEndOfStock = view.findViewById(R.id.endOfStock);
                mIconView = view.findViewById(R.id.list_image);
            }

            @NonNull
            @Override
            public String toString() {
                return super.toString() + " '" + mContentView.getText() + "'";
            }
        }
    }

    private String getAppName() {
        PackageManager packageManager = getApplicationContext().getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(this.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException ignored) {
        }
        return (String) ((applicationInfo != null) ? packageManager.getApplicationLabel(applicationInfo) : "???");
    }
}
