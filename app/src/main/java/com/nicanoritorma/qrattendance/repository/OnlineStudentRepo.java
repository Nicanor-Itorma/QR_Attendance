package com.nicanoritorma.qrattendance.repository;

import static com.nicanoritorma.qrattendance.BaseActivity.getDbUrl;

import android.app.Application;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;

import com.nicanoritorma.qrattendance.BaseActivity;
import com.nicanoritorma.qrattendance.api.GetStudentOnline;
import com.nicanoritorma.qrattendance.api.PutData;
import com.nicanoritorma.qrattendance.model.StudentModel;

import java.util.List;

public class OnlineStudentRepo {

    private static OnlineStudentRepo instance;
    private Application application;
    private GetStudentOnline studentList;

    public OnlineStudentRepo(Application application)
    {
        this.application = application;
        studentList = new GetStudentOnline(application);
    }

    public void insert(StudentModel student)
    {
        new InsertStudentToDb(student.getName(), student.getIdNum(), student.getCollege(), student.getQrCode()).execute();
    }

    static class InsertStudentToDb extends AsyncTask<Void, Void, String>
    {
        String url = getDbUrl() + "AddQrToDb.php";
        String fullname, idNumber, dept, qrCode;
        String result;

        public InsertStudentToDb(String fullname, String idNumber, String dept, String qrCode) {
            this.fullname = fullname;
            this.idNumber = idNumber;
            this.dept = dept;
            this.qrCode = qrCode;
        }

        @Override
        protected String doInBackground(Void... voids) {
            String[] field = new String[4];
            field[0] = "fullname";
            field[1] = "idNumber";
            field[2] = "department";
            field[3] = "qrCode";

            String[] data = new String[4];
            data[0] = fullname;
            data[1] = idNumber;
            data[2] = dept;
            data[3] = qrCode;

            //TODO: change URL address
            PutData putData = new PutData(url, "POST", field, data);
            if (putData.startPut()) {
                if (putData.onComplete()) {
                    if (putData.getResult().equals("Success"))
                    {
                        result = "Success";
                    }
                }
            }
            return null;
        }
    }

    public LiveData<List<StudentModel>> getStudentList()
    {
        return studentList.getStudentList();
    }
}
