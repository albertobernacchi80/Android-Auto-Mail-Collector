package com.androidauto.mailcollector;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.validation.HostValidator;
import androidx.car.app.Session;

public final class MailCarAppService extends CarAppService {
    @NonNull @Override public HostValidator createHostValidator() {
        // Va bene per i test locali su Android Auto; prima di una eventuale distribuzione più
        // ampia andrebbe sostituito con l'allow-list ufficiale, come nel progetto MX-5 Driver
        // Metrics.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull @Override public Session onCreateSession() {
        return new MailSession();
    }
}
