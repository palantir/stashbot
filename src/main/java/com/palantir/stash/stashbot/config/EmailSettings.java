// Copyright 2014 Palantir Technologies
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
