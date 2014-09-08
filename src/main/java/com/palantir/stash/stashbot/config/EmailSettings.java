package com.palantir.stash.stashbot.config;

public class EmailSettings {
    private Boolean emailNotificationsEnabled;
    private final String emailRecipients;
    private final Boolean emailForEveryUnstableBuild;
    private final Boolean emailSendToIndividuals;
    private final Boolean emailPerModuleEmail;

    public EmailSettings() {
        this(false, "", false, false, false);
    }

    public EmailSettings(Boolean emailNotificationsEnabled, String emailRecipients, Boolean emailForEveryUnstableBuild, Boolean emailSendToIndividuals, Boolean emailPerModuleEmail) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.emailRecipients = emailRecipients;
        this.emailForEveryUnstableBuild = emailForEveryUnstableBuild;
        this.emailSendToIndividuals = emailSendToIndividuals;
        this.emailPerModuleEmail = emailPerModuleEmail;
    }

    public Boolean getEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public String getEmailRecipients() {
        return emailRecipients;
    }

    public Boolean getEmailForEveryUnstableBuild() {
        return emailForEveryUnstableBuild;
    }

    public Boolean getEmailSendToIndividuals() {
        return emailSendToIndividuals;
    }

    public Boolean getEmailPerModuleEmail() {
        return emailPerModuleEmail;
    }
}
