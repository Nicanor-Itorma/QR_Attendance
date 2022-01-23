package com.nicanoritorma.qrattendance;

import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.nicanoritorma.qrattendance.OfflineViewModels.AttendanceVM;
import com.nicanoritorma.qrattendance.OfflineViewModels.StudentInAttendanceVM;
import com.nicanoritorma.qrattendance.model.AttendanceModel;
import com.nicanoritorma.qrattendance.model.StudentInAttendanceModel;
import com.nicanoritorma.qrattendance.ui.adapter.AttendanceAdapter;
import com.nicanoritorma.qrattendance.utils.RecyclerViewItemClickSupport;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AttendanceList extends BaseActivity {

    private RecyclerView rv_attendanceList;
    private AttendanceAdapter attendanceAdapter;
    private ActionMode actionMode;
    private final List<AttendanceModel> selectedAttendance = new ArrayList<>();
    private final ArrayList<String> itemInAttendance = new ArrayList<>();
    private static Application application;
    private String attendanceName;
    private File file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_list);

        AttendanceList.application = getApplication();
        rv_attendanceList = findViewById(R.id.rv_attendanceList);

        initUI();
    }

    private void initUI() {
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle("Attendance List");
        }

        rv_attendanceList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        rv_attendanceList.setHasFixedSize(true);
        attendanceAdapter = new AttendanceAdapter();
        rv_attendanceList.setAdapter(attendanceAdapter);

        //offline attendance list
        AttendanceVM attendanceVM = new AttendanceVM(getApplication());
        attendanceVM.getAllAttendance().observe(this, new Observer<List<AttendanceModel>>() {
            @Override
            public void onChanged(List<AttendanceModel> attendanceModels) {
                attendanceAdapter.setList(attendanceModels);

                RecyclerViewItemClickSupport.addTo(rv_attendanceList).setOnItemClickListener(new RecyclerViewItemClickSupport.OnItemClickListener() {
                    @Override
                    public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                        if (actionMode == null) {
                            AttendanceModel attendanceModel = attendanceModels.get(position);
                            openClickedAttendance(new AttendanceModel(attendanceModel.getId(), attendanceModel.getAttendanceName(), attendanceModel.getDetails(),
                                    attendanceModel.getDate(), attendanceModel.getTime()));
                            return;
                        }
                        //if in CAB mode
                        toggleListViewItem(v, position);
                        setCABTitle();
                    }
                }).setOnItemLongClickListener(new RecyclerViewItemClickSupport.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClicked(RecyclerView recyclerView, int position, View v) {
                        if (actionMode != null) {
                            return false;
                        }
                        // Start the CAB using the ActionMode.Callback defined below
                        AttendanceList.this.startActionMode(new ModeCallback());
                        toggleListViewItem(v, position);
                        setCABTitle();
                        return true;
                    }
                });
            }
        });
    }

    public void toggleListViewItem(View view, int position) {
        AttendanceModel attendance = attendanceAdapter.getItem(position);
        RelativeLayout item = view.findViewById(R.id.item_relative_layout);

        if (!getSelectedAttendance().contains(attendance)) {
            getSelectedAttendance().add(attendance);
            attendanceAdapter.addSelectedItem(position);
            item.setBackgroundResource(R.drawable.selected_item_border);
        } else {
            getSelectedAttendance().remove(attendance);
            attendanceAdapter.removeSelectedItem(position);
            attendanceAdapter.restoreDrawable(item);
        }
        prepareActionModeMenu();

        if (selectedAttendance.isEmpty()) {
            finishActionMode();
        }

        setCABTitle();
    }

    private void prepareActionModeMenu() {
        Menu menu = actionMode.getMenu();
        if (attendanceAdapter.getItemCount() > 1) {
            menu.findItem(R.id.menuActionSelectAll).setVisible(true);

        }
        menu.findItem(R.id.menu_saveToDevice).setVisible(true);
        menu.findItem(R.id.menuActionDelete).setVisible(true);
    }

    private void setCABTitle() {
        if (actionMode != null) {
            int title = selectedAttendance.size();
            actionMode.setTitle(String.valueOf(title));
        }
    }

    private List<AttendanceModel> getSelectedAttendance() {
        return selectedAttendance;
    }

    public void finishActionMode() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private void selectAllAttendance() {
        for (int i = 0; i < rv_attendanceList.getChildCount(); i++) {
            RelativeLayout item = rv_attendanceList.getChildAt(i).findViewById(R.id.item_relative_layout);
            item.setBackgroundResource(R.drawable.selected_item_border);
        }
        selectedAttendance.clear();
        for (int i = 0; i < attendanceAdapter.getItemCount(); i++) {
            selectedAttendance.add(attendanceAdapter.getItem(i));
            attendanceAdapter.addSelectedItem(i);
        }
        prepareActionModeMenu();
        setCABTitle();
    }

    private final class ModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            actionMode = mode;
            inflater.inflate(R.menu.app_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            prepareActionModeMenu();
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

            switch (item.getItemId()) {
                case R.id.menuActionSelectAll:
                    selectAllAttendance();
                    return true;
                case R.id.menuActionDelete:
                    for (int i = 0; i < selectedAttendance.size(); i++) {
                        AttendanceModel attendance = selectedAttendance.get(i);
                        deleteItem(attendance);
                    }
                    selectedAttendance.clear();
                    finishActionMode();
                    return true;
                case R.id.menu_saveToDevice:
                    showProgressBar(true);
                    int id = 0;
                    attendanceName = selectedAttendance.get(0).getAttendanceName();
                    id = selectedAttendance.get(0).getId();

                    prepareData(id);
                    return true;
            }
            mode.finish();
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectedAttendance.clear();
            attendanceAdapter.clearSelectedItems();
            attendanceAdapter.notifyDataSetChanged();
            actionMode = null;
        }
    }

    private void prepareData(int parentId) {
        StudentInAttendanceVM student = new StudentInAttendanceVM(application);
        student.getStudentList(parentId).observe(this, new Observer<List<StudentInAttendanceModel>>() {
            @Override
            public void onChanged(List<StudentInAttendanceModel> studentInAttendanceModels) {
                for (StudentInAttendanceModel student : studentInAttendanceModels) {
                    itemInAttendance.add(student.getFullname() + ";" + student.getIdNum());
                }
            }
        });
        exportDataToCSV();
    }

    /*
        Attendance will be saved to device storage
    */
    private void exportDataToCSV() {
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String uniqueFileName = attendanceName + ".csv";
        File dir = new File(directory.getAbsolutePath() + "/QR_attendance/Attendance");
        if (!dir.exists())
            dir.mkdirs();

        Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(new Runnable() {
            @Override
            public void run() {
                StringBuilder csvData = new StringBuilder();

                for (int i = 0; i < itemInAttendance.size(); i++) {

                    String currentLIne = itemInAttendance.get(i);
                    String[] cells = currentLIne.split(";");

                    csvData.append(toCSV(cells)).append("\n");
                }
                //recall method again to write data on created file
                exportDataToCSV();
                try {
                    file = new File(dir, uniqueFileName);
                    FileWriter fileWriter = new FileWriter(file);
                    fileWriter.write(csvData.toString());
                    fileWriter.flush();
                    fileWriter.close();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showProgressBar(false);
                    }
                });
            }
        });
    }

    public static String toCSV(String[] array) {
        String result = "";
        if (array.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (String s : array) {
                sb.append(s.trim()).append(",");
            }
            result = sb.deleteCharAt(sb.length() - 1).toString();
        }
        return result;
    }

    private void deleteItem(AttendanceModel attendance) {
        AttendanceVM attendanceVM = new AttendanceVM(application);
        attendanceVM.delete(attendance);
    }

    private void openClickedAttendance(AttendanceModel attendance) {
        Intent intent = new Intent(AttendanceList.this, ClickedAttendance.class);
        intent.putExtra("ITEM_ID", attendance.getId());
        intent.putExtra(ClickedAttendance.EXTRA_ATTENDANCE_NAME, attendance.getAttendanceName());
        intent.putExtra(ClickedAttendance.EXTRA_DETAILS, attendance.getDetails());
        intent.putExtra(ClickedAttendance.EXTRA_DATE, attendance.getDate());
        intent.putExtra(ClickedAttendance.EXTRA_TIME, attendance.getTime());
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}