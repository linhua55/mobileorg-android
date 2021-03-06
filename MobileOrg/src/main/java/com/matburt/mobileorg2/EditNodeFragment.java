package com.matburt.mobileorg;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TimePicker;

import com.matburt.mobileorg.OrgData.OrgContract;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeTimeDate;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;
import com.matburt.mobileorg.util.TodoDialog;

import java.util.ArrayList;
import java.util.Calendar;

public class EditNodeFragment extends Fragment {
    public static String NODE_ID = "node_id";
    public static String PARENT_ID = "parent_id";
    static public long nodeId = -1, parentId = -1;
    static Button schedule_date, deadline_date, schedule_time, deadline_time;
    static OrgNodeTimeDate.TYPE currentDateTimeDialog;
    static private OrgNode node;
    EditText title, content;
    Context context;
    private int position = 0;
    private Button todo, priority;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.edit_node_entry, container, false);
        context = getContext();

        todo          = (Button) rootView.findViewById(R.id.todo);
        priority      = (Button) rootView.findViewById(R.id.priority);
        schedule_date = (Button) rootView.findViewById(R.id.scheduled_date);
        deadline_date = (Button) rootView.findViewById(R.id.deadline_date);

        title   = (EditText) getActivity().findViewById(R.id.title);
        content = (EditText) getActivity().findViewById(R.id.content);

        Bundle bundle = getArguments();
        if(bundle!=null){
            nodeId = bundle.getLong(NODE_ID, -1);
            parentId = bundle.getLong(PARENT_ID, -1);
            position = bundle.getInt(OrgContract.OrgData.POSITION, 0);
            Log.v("position","position : "+position);
        }

        ContentResolver resolver = getActivity().getContentResolver();

        if(nodeId > -1) {
            // Editing already existing node
            try {
                node = new OrgNode(nodeId, resolver);
            } catch (OrgNodeNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            createNewNode(resolver);
        }

        TodoDialog.setupTodoButton(getContext(), node, todo, false);

        todo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TodoDialog(getContext(), node, todo);
            }
        });

        priority.setText(node.priority);
        priority.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPriorityDialog();
            }
        });

        title.setText(node.name);

        String payload = node.getCleanedPayload();
        if(payload.length()>0){
            content.setText(payload);
        }

        title.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                title.setFocusable(true);
                title.requestFocus();
                return false;
            }
        });

        final LinearLayout layout = (LinearLayout)rootView.findViewById(R.id.view_fragment_layout);
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                layout.requestFocus();
                return true;
            }
        });

        schedule_date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentDateTimeDialog = OrgNodeTimeDate.TYPE.Scheduled;
                setupDateTimeDialog();
            }
        });

        deadline_date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentDateTimeDialog = OrgNodeTimeDate.TYPE.Deadline;
                setupDateTimeDialog();
            }
        });

        setupTimeStampButtons();

        getActivity().invalidateOptionsMenu();
        return rootView;
    }

    static private void setupTimeStampButtons() {
        String scheduleText = node.getScheduled().getDate();
        String deadlineText = node.getDeadline().getDate();
        if (scheduleText.length() > 0) schedule_date.setText(scheduleText);
        if (deadlineText.length() > 0) deadline_date.setText(deadlineText);
    }

    private void createNewNode(ContentResolver resolver){
        // Creating new node
        node = new OrgNode();
        node.parentId = parentId;
        node.position = position;
        Log.v("newNode","parentId : "+parentId);
        try {
            OrgNode parentNode = new OrgNode(parentId, resolver);
            node.level = parentNode.level + 1;
            node.fileId = parentNode.fileId;
            Log.v("newNode","fileId : "+node.fileId);
        } catch (OrgNodeNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by EditNodeActivity when the OK button from the menu bar is pressed
     * Triggers the update mechanism
     * First the new node is written to the DB
     * Then the file is written to disk
     */
    public void onOKPressed(){
        ContentResolver resolver = getContext().getContentResolver();
        String payload = "";

        payload+=content.getText().toString();

        node.name = title.getText().toString();
        node.setPayload(payload);

        if(nodeId <0 ) node.shiftNextSiblingNodes(context);

        node.write(getContext());
        OrgFile.updateFile(node, context);

    }

    /**
     * Called by EditNodeActivity when the Cancel button from the menu bar is pressed
     */
    public void onCancelPressed(){
    }

    private void setupDateTimeDialog(){
        DialogFragment newFragment = new DatePickerFragment();
        newFragment.show(getActivity().getSupportFragmentManager(), "datePicker");
    }

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {
        static private int day = -1, month = -1, year = -1;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar c = Calendar.getInstance();
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            day = getArguments().getInt("day");
            month = getArguments().getInt("month");
            year = getArguments().getInt("year");

            // Create a new instance of TimePickerDialog and return it
            return new TimePickerDialog(getActivity(), this, hour, minute,
                    DateFormat.is24HourFormat(getActivity()));
        }

        public void onTimeSet(TimePicker view, int hourOfDay, int minuteOfDay) {
            ContentResolver resolver = getActivity().getContentResolver();

            node.getOrgNodePayload().insertOrReplaceDate(
                    new OrgNodeTimeDate(
                            EditNodeFragment.currentDateTimeDialog,
                            day,
                            month,
                            year,
                            hourOfDay,
                            minuteOfDay
                    )
            );
            Log.v("timestamp","test : "+node.getOrgNodePayload().getTimestamp(OrgNodeTimeDate.TYPE.Scheduled));

        }
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            node.addDate(
                    new OrgNodeTimeDate(
                            EditNodeFragment.currentDateTimeDialog,
                            day,
                            month,
                            year
                    )
            );

            setupTimeStampButtons();
//            Bundle bundle = new Bundle();
//            bundle.putInt("year",year);
//            bundle.putInt("month",month);
//            bundle.putInt("day",day);
//            TimePickerFragment newFragment = new TimePickerFragment();
//            newFragment.setArguments(bundle);
//            newFragment.show(getActivity().getSupportFragmentManager(), "timePicker");
        }
    }

    static public void createEditNodeFragment(int id, int parentId, int siblingPosition, Context context) {
        Bundle args = new Bundle();
        args.putLong(OrgContract.NODE_ID, id);
        args.putLong(OrgContract.PARENT_ID, parentId);
        args.putInt(OrgContract.OrgData.POSITION, siblingPosition);

        Intent intent = new Intent(context, EditNodeActivity.class);
        intent.putExtras(args);
        context.startActivity(intent);
    }

    private void showPriorityDialog(){
        final ArrayList<String> priorityList = new ArrayList<>();
        priorityList.add("A");
        priorityList.add("B");
        priorityList.add("C");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.todo_state)
                .setItems(priorityList.toArray(new CharSequence[priorityList.size()]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                String selectedPriority = priorityList.get(which);
                                node.priority = selectedPriority;
//                                setupTodoButton(context,node,button, false);
                                priority.setText(selectedPriority);
                            }
                        });
        builder.create().show();
    }
}
