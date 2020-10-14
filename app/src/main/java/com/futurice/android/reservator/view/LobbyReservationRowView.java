package com.futurice.android.reservator.view;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.futurice.android.reservator.R;
import com.futurice.android.reservator.ReservatorApplication;
import com.futurice.android.reservator.RoomActivity;
import com.futurice.android.reservator.common.PreferenceManager;
import com.futurice.android.reservator.model.AddressBookAdapter;
import com.futurice.android.reservator.model.AddressBookEntry;
import com.futurice.android.reservator.model.DateTime;
import com.futurice.android.reservator.model.ReservatorException;
import com.futurice.android.reservator.model.Room;
import com.futurice.android.reservator.model.TimeSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import butterknife.BindView;
import butterknife.ButterKnife;

import static java.util.stream.Collectors.toList;

public class LobbyReservationRowView extends FrameLayout implements
        OnClickListener, OnItemClickListener {

    @BindView(R.id.cancelButton)
    ImageButton cancelButton;
    @BindView(R.id.reserveButton)
    Button reserveButton;
    @BindView(R.id.hintText)
    TextView hintText;
    @BindView(R.id.titleLayout)
    View titleView;
    @BindView(R.id.calendarButton)
    Button calendarButton;
    @BindView(R.id.bookingMode)
    View bookingMode;
    @BindView(R.id.normalMode)
    View normalMode;
    @BindView(R.id.eventAttendees)
    MultiAutoCompleteTextView attendeesField;
    @BindView(R.id.eventTitle)
    TextView eventTitle;
    @BindView(R.id.timeSpanPicker2)
    CustomTimeSpanPicker2 timePicker2;
    @BindView(R.id.roomNameLabel)
    TextView roomNameView;
    @BindView(R.id.roomInfoLabel)
    TextView roomInfoView;
    @BindView(R.id.roomStatusLabel)
    TextView roomStatusView;
    @BindView(R.id.modeSwitcher)
    ViewSwitcher modeSwitcher;
    @BindView(R.id.roomDefaultIcon)
    ImageView defaultRoomFlag;

    ReservatorApplication application;
    OnReserveListener onReserveCallback = null;
    OnCancelListener OnCancelListener = null;

    private Room room;
    private int animationDuration = 300;
    private ReservatorException reservatorException;

    private OnFocusChangeListener userNameFocusChangeListener = (View v, boolean hasFocus) -> {
      Boolean addressBookOption = PreferenceManager.getInstance(getContext()).getAddressBookEnabled();
      if(hasFocus && addressBookOption)
          reserveButton.setEnabled(false);
    };

    public LobbyReservationRowView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public LobbyReservationRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.lobby_reservation_row, this);
        ButterKnife.bind(this);
        cancelButton.setOnClickListener(this);
        titleView.setOnClickListener(this);
        reserveButton.setOnClickListener(this);
        calendarButton.setOnClickListener(this);
        switchToNormalModeContent();

        application = (ReservatorApplication) this.getContext()
                .getApplicationContext();

        eventTitle.setOnClickListener(this);

        attendeesField.setOnFocusChangeListener(userNameFocusChangeListener);
        attendeesField.setOnItemClickListener(this);
        attendeesField.setOnClickListener(this);
        if(attendeesField.getAdapter() == null) {
            try {
                attendeesField.setAdapter(new AddressBookAdapter(this.getContext(),
                        application.getAddressBook()));
            } catch (ReservatorException ex) {
                reservatorException = ex;
            }
        }
        attendeesField.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
    }

    public void setAnimationDuration(int millis) {
        animationDuration = millis;
    }

    public ReservatorException getException() {
        return reservatorException;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;

        // Room stuff
        roomNameView.setText(room.getName());

        if (room.getCapacity() >= 0) {
            roomInfoView.setText(getContext().getString(R.string.room_capacity, room.getCapacity()));
        } else {
            roomInfoView.setText("");
        }

        // Reservation stuff
        TimeSpan nextFreeTime = room.getNextFreeTime();

        timePicker2.reset();
        if (nextFreeTime != null) {
            timePicker2.setMinimumTime(nextFreeTime.getStart());
            timePicker2.setMaximumTime(nextFreeTime.getEnd());
        } else {
            timePicker2.setMinimumTime(new DateTime());
        }
        timePicker2.setEndTimeRelatively(60); // let book the room for an hour

        roomStatusView.setText(room.getStatusText());
        if (room.isBookable()) {
            roomStatusView.setTextColor(getResources().getColor(
                    R.color.StatusFreeColor));
        } else {
            roomStatusView.setTextColor(getResources().getColor(
                    R.color.StatusReservedColor));
        }

        if (application.getFavouriteRoomName().equals(room.getName())) {
            defaultRoomFlag.setVisibility(VISIBLE);
            roomNameView.setTypeface(null, Typeface.BOLD);
        } else {
            defaultRoomFlag.setVisibility(INVISIBLE);
            roomNameView.setTypeface(null, Typeface.NORMAL);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == calendarButton || v == titleView) {
            RoomActivity.startWith(getContext(), getRoom());
        } else if (v == cancelButton) {
            setNormalMode();
            if (this.OnCancelListener != null) {
                this.OnCancelListener.onCancel(this);
            }
        } else if (v == reserveButton) {
            reserveButton.setEnabled(false);
            new MakeReservationTask().execute();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        reserveButton.setEnabled(true);
        attendeesField.setSelected(false);
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(attendeesField.getRootView().getWindowToken(), 0);
    }

    private void reservatorError(ReservatorException e) {
        Builder alertBuilder = new AlertDialog.Builder(getContext());
        alertBuilder.setTitle("Failed to put reservation").setMessage(
                e.getMessage());
        alertBuilder.setOnCancelListener((DialogInterface dialog) -> {
            if (onReserveCallback != null) {
                onReserveCallback.call(LobbyReservationRowView.this);
            }
        });

        alertBuilder.show();
    }

    public void setNormalMode() {
        if (animationDuration > 0) {
            Animation scale = new ScaleAnimation(1.0f, 1.0f, 1.0f, 0.0f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(animationDuration);
            scale.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    switchToNormalModeContent();
                    Animation scale = new ScaleAnimation(1.0f, 1.0f, 0.0f, 1.0f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
                    scale.setDuration(animationDuration);
                    startAnimation(scale);
                }
            });
            startAnimation(scale);
        } else {
            switchToNormalModeContent();
        }
    }

    private void switchToNormalModeContent() {
        modeSwitcher.setDisplayedChild(modeSwitcher.indexOfChild(normalMode));
        if (modeSwitcher.indexOfChild(bookingMode) >= 0) {
            modeSwitcher.removeView(bookingMode);
        }
        reserveButton.setEnabled(false);
        setBackgroundColor(getResources().getColor(R.color.Transparent));
    }

    public void setReserveMode() {
        if (animationDuration > 0) {
            Animation scale = new ScaleAnimation(1.0f, 1.0f, 1.0f, 0.0f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(animationDuration);
            scale.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    switchToReserveModeContent();
                    Animation scale = new ScaleAnimation(1.0f, 1.0f, 0.0f, 1.0f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
                    scale.setDuration(animationDuration);
                    startAnimation(scale);
                }
            });
            startAnimation(scale);
        } else {
            switchToReserveModeContent();
        }
    }

    private void switchToReserveModeContent() {
        if (modeSwitcher.indexOfChild(bookingMode) < 0) {
            modeSwitcher.addView(bookingMode);
        }
        modeSwitcher.setDisplayedChild(modeSwitcher.indexOfChild(bookingMode));
        setBackgroundColor(getResources().getColor(R.color.ReserveBackground));

        // Initial state for the "Reserve" button.
        if (PreferenceManager.getInstance(getContext()).getAddressBookEnabled()) {
            reserveButton.setEnabled(false);
            hintText.setVisibility(View.GONE);
        } else {
            reserveButton.setEnabled(true);
            hintText.setVisibility(View.VISIBLE);
        }
    }

    public void resetTimeSpan() {
        timePicker2.reset();
    }

    public void setMinTime(DateTime time) {
        timePicker2.setMinimumTime(time);
    }

    public void setMaxTime(DateTime time) {
        timePicker2.setMaximumTime(time);
    }

    public void setEndTimeRelatively(int minutes) {
        timePicker2.setEndTimeRelatively(minutes);
    }

    public void setOnReserveCallback(OnReserveListener onReserveCallback) {
        this.onReserveCallback = onReserveCallback;
    }

    public void setOnCancelListener(OnCancelListener l) {
        this.OnCancelListener = l;
    }

    public interface OnCancelListener {
        void onCancel(LobbyReservationRowView view);
    }

    public interface OnReserveListener {
        void call(LobbyReservationRowView v);
    }

    private class MakeReservationTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            Boolean addressBookOption = PreferenceManager.getInstance(getContext()).getAddressBookEnabled();
            String title = eventTitle.getText().toString();
            String[] attendees = attendeesField.getText()
                    .toString()
                    .trim()
                    .split("\\s*,\\s*");

            // Set the first attendee to be the organisator of the event,
            // this does not function at the moment because we are using the
            // tablet email for booking an event
            AddressBookEntry entry = application.getAddressBook().getEntryByName(attendees[0]);
            List<AddressBookEntry> bookEntries = Stream.of(attendees)
                    .filter(attendee -> application.getAddressBook().getEntryByName(attendee) != null)
                    .map(attendee -> application.getAddressBook().getEntryByName(attendee))
                    .collect(toList());

            if (entry == null && addressBookOption) {
                reservatorError(new ReservatorException("No such user, try again"));
            }
            try {
                if (entry != null) {
                    application.getDataProxy().reserve(room, timePicker2.getTimeSpan(), title, entry, bookEntries);
                } else {
                    // Address book option is off so reserve the room with the selected account in settings.
                    String accountEmail = PreferenceManager.getInstance(getContext()).getDefaultUserName();
                    if (accountEmail.equals("")) {
                        reservatorError(new ReservatorException("No account for reservation stored. Check your settings."));
                    }
                    if (title.equals("")) {
                        title = application.getString(R.string.defaultTitleForReservation);
                    }
                    application.getDataProxy().reserve(room, timePicker2.getTimeSpan(),
                            title, entry, bookEntries);
                }
            } catch (ReservatorException e) {
                reservatorError(e);
            }

            // Void requires "return null;". Java blah.
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            onReserveCallback.call(LobbyReservationRowView.this);
        }
    }
}
