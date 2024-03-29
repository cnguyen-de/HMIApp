package hmi.hmiprojekt;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class RenameTripDialog extends AppCompatDialogFragment {

    private EditText editTextTripName;
    private RenameTripDialog.RenameTripDialogListener listener;



    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            listener = (RenameTripDialog.RenameTripDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(e.toString() +
                    "must implement NewTripDialogListener");
        }
    }



    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // build view
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_new_trip, null);
        builder.setView(view)
                .setTitle("Trip umbenennen")
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String tripName = editTextTripName.getText().toString();
                        listener.returnNewTripName(tripName);
                    }
                });

        editTextTripName = view.findViewById(R.id.editText_trip_name);
        return builder.create();
    }

    public interface RenameTripDialogListener {
        void returnNewTripName(String tripName);
    }
}
