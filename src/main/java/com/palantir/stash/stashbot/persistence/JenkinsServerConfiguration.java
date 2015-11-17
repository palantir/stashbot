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
package com.palantir.stash.stashbot.persistence;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Implementation;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.Ignore;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Table("JSConfig001")
@Preload
@Implementation(JenkinsServerConfigurationImpl.class)
public interface JenkinsServerConfiguration extends Entity {

    static public enum AuthenticationMode {
        // NOTE: when you add stuff here, edit StashbotUrlBuilder as well.
        CREDENTIAL_AUTOMATIC_SSH_KEY(Constants.CASK_VALUE, "Automatically Configured SSH Key Credential UUID"),
        USERNAME_AND_PASSWORD(Constants.UAP_VALUE, "Username and Password"),
        CREDENTIAL_MANUALLY_CONFIGURED(Constants.CMC_VALUE, "Manually Configured Credential UUID");

        private final String description;
        private final String mode;

        // This is necessary because AO annotations require static string constants
        public static class Constants {

            public static final String UAP_VALUE = "USERNAME_AND_PASSWORD";
            public static final String CMC_VALUE = "CREDENTIAL_MANUALLY_CONFIGURED";
            public static final String CASK_VALUE = "CREDENTIAL_AUTOMATIC_SSH_KEY";
        }

        AuthenticationMode(String mode, String description) {
            this.description = description;
            this.mode = mode;
        }

        public String getDescription() {
            return description;
        }

        public String getMode() {
            return mode;
        }

        public static AuthenticationMode fromMode(String mode) {
            if (mode.equals(Constants.UAP_VALUE)) {
                return USERNAME_AND_PASSWORD;
            }
            if (mode.equals(Constants.CMC_VALUE)) {
                return CREDENTIAL_MANUALLY_CONFIGURED;
            }
            if (mode.equals(Constants.CASK_VALUE)) {
                return CREDENTIAL_AUTOMATIC_SSH_KEY;
            }
            throw new IllegalArgumentException("invalid value for enum: " + mode);
        }

        public static String toMode(AuthenticationMode am) {
            return am.getMode();
        }

        /**
         * Helper method for populating a dropdown option box with metadata
         * 
         * @param selected
         * @return
         */
        public ImmutableMap<String, String> getSelectListEntry(boolean selected) {
            if (selected) {
                return ImmutableMap.of("text", this.getDescription(), "value", this.toString(), "selected", "true");
            } else {
                return ImmutableMap.of("text", this.getDescription(), "value", this.toString());
            }
        }

        /**
         * Helper method for populating a dropdown option box with metadata
         * 
         * @param selected
         * @return
         */
        public static ImmutableList<ImmutableMap<String, String>> getSelectList(AuthenticationMode selected) {
            ImmutableList.Builder<ImmutableMap<String, String>> builder = ImmutableList.builder();
            for (AuthenticationMode ae : AuthenticationMode.values()) {
                if (selected != null && selected.equals(ae)) {
                    builder.add(ae.getSelectListEntry(true));
                } else {
                    builder.add(ae.getSelectListEntry(false));
                }
            }
            return builder.build();
        }
    }

    @NotNull
    @Unique
    public String getName();

    public void setName(String username);

    @NotNull
    @Default("empty")
    public String getUrl();

    public void setUrl(String url);

    @NotNull
    @Default("empty")
    public String getUsername();

    public void setUsername(String username);

    @NotNull
    @Default("empty")
    public String getPassword();

    public void setPassword(String password);

    // New Credential handling - enum stored as Const TXT in the DB
    @NotNull
    @Default(AuthenticationMode.Constants.UAP_VALUE)
    public String getAuthenticationModeStr();

    public void setAuthenticationModeStr(String authMode);

    @NotNull
    @Default("empty")
    public String getStashUsername();

    public void setStashUsername(String stashUsername);

    @NotNull
    @Default("empty")
    public String getStashPassword();

    public void setStashPassword(String stashPassword);

    @Default("empty")
    public String getCredentialId();

    public void setCredentialId(String credentialId);

    /**
     * Maximum number of verify builds to trigger when pushed all at once. This limit makes it so that if you push a
     * chain of 100 new commits all at once, instead of saturating your build hardware, only the N most recent commits
     * are built. Set to "0" for infinite. Default is 10.
     */
    @NotNull
    @Default("10")
    public Integer getMaxVerifyChain();

    public void setMaxVerifyChain(Integer max);

    // For security - allow a jenkins server config to be locked to non-system-admins
    @NotNull
    @Default("false")
    @Accessor("LOCKED")
    public Boolean getLocked();

    @Mutator("LOCKED")
    public void setLocked(Boolean isLocked);

    // Enable folder support
    @NotNull
    @Default("false")
    @Accessor("FOLDER_SUPPORT")
    public Boolean getFolderSupportEnabled();

    @Mutator("FOLDER_SUPPORT")
    public void setFolderSupportEnabled(Boolean isEnabled);

    @NotNull
    @Default("false")
    @Accessor("USE_SUBFOLDERS")
    public Boolean getUseSubFolders();

    @Mutator("USE_SUBFOLDERS")
    public void setUseSubFolders(Boolean useSubFolders);

    @NotNull
    @Default("/")
    @Accessor("FOLDER_PREFIX")
    public String getFolderPrefixRaw();

    @Mutator("FOLDER_PREFIX")
    public void setFolderPrefixRaw(String folderPrefix);

    /////
    // These are implemented in JenkinsServerConfigurationImpl - so the user can use enums
    /////
    @Ignore
    public AuthenticationMode getAuthenticationMode();

    @Ignore
    public void setAuthenticationMode(AuthenticationMode authMode);

    /////
    // These are implemented in JenkinsServerConfigurationImpl - to make AO's insane inability to store empty strings transparent
    /////
    @Ignore
    public String getFolderPrefix();

    @Ignore
    public void setFolderPrefix(String folderPrefix);
}
