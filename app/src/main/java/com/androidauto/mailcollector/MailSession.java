package com.androidauto.mailcollector;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.car.app.Session;
import androidx.car.app.Screen;

public final class MailSession extends Session {
    @NonNull @Override public Screen onCreateScreen(@NonNull Intent intent) {
        return new InboxListScreen(getCarContext());
    }
}
