package com.carbox.launcher;

import android.service.notification.NotificationListenerService;

/**
 * Empty notification listener. Android only shares "what's playing" with apps the user has
 * granted notification access to, so this service exists purely to receive that permission.
 */
public class MediaListener extends NotificationListenerService {
}
