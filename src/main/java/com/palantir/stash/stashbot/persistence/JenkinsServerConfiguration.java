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

import com.atlassian.stash.repository.Repository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Table("JSConfig001")
@Preload
@Implementation(JenkinsServerConfigurationImpl.class)
public interface JenkinsServerConfiguration extends Entity {

    public static final Integer BUILD_TIMEOUT_MINUTES_MIN = 5;
    public static final Integer BUILD_TIMEOUT_MINUTES_MAX = 7 * 24 * 60; // One week
    public static final Integer BUILD_TIMEOUT_MINUTES_DEFAULT = 4 * 60; // Four hours

	static public enum AuthenticationMode {
		// NOTE: when you add stuff here, edit StashbotUrlBuilder as well.
		USERNAME_AND_PASSWORD(Constants.UAP_VALUE, "Username and Password"), CREDENTIAL_MANUALLY_CONFIGURED(
				Constants.CMC_VALUE, "Manually Configured Credential UUID");

		// TODO?
		// CREDENTIAL_USERNAME_AND_PASSWORD(Constants.CUAP_VALUE),
		// CREDENTIAL_SSH_KEY(Constants.CSSH_VALUE);
		private final String description;
		private final String mode;

		// This is necessary because AO annotations require static string
		// constants
		public static class Constants {

			public static final String UAP_VALUE = "USERNAME_AND_PASSWORD";
			public static final String CMC_VALUE = "CREDENTIAL_MANUALLY_CONFIGURED";
			// public static final String CUAP_VALUE = "CUAP";
			// public static final String CSSH_VALUE = "CSSH";
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
			throw new IllegalArgumentException("invalid value for enum: "
					+ mode);
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
				return ImmutableMap.of("text", this.getDescription(), "value",
						this.toString(), "selected", "true");
			} else {
				return ImmutableMap.of("text", this.getDescription(), "value",
						this.toString());
			}
		}

		/**
		 * Helper method for populating a dropdown option box with metadata
		 *
		 * @param selected
		 * @return
		 */
		public static ImmutableList<ImmutableMap<String, String>> getSelectList(
				AuthenticationMode selected) {
			ImmutableList.Builder<ImmutableMap<String, String>> builder = ImmutableList
					.builder();
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

	// ///
	// These are implemented in JenkinsServerConfigurationImpl - so the user can
	// use enums
	// ///
	public AuthenticationMode getAuthenticationMode();

	public void setAuthenticationMode(AuthenticationMode authMode);

	@NotNull
	@Default("empty")
	public String getStashUsername();

	public void setStashUsername(String stashUsername);

	@NotNull
	@Default("empty")
	public String getStashPassword();

	public void setStashPassword(String stashPassword);

	/**
	 * Maximum number of verify builds to trigger when pushed all at once. This
	 * limit makes it so that if you push a chain of 100 new commits all at
	 * once, instead of saturating your build hardware, only the N most recent
	 * commits are built. Set to "0" for infinite. Default is 10.
	 */
	@NotNull
	@Default("10")
	public Integer getMaxVerifyChain();

	public void setMaxVerifyChain(Integer max);

	@NotNull
	@Default("/")
	public String getPrefixTemplate();
	public void setPrefixTemplate(String template);

    @NotNull
    @Default("$project_$repo")
    public String getJobTemplate();

    public void setJobTemplate(String template);

	// Implemented in JenkinsServerConfigurationImpl - expands variables in
	// template and appends to url.
	@Ignore
	public String getUrlForRepo(Repository r);


	@NotNull
	@Default("240") // Four Hours in minutes
	public Integer getDefaultTimeout();
	public void setDefaultTimeout(Integer defaultMinutes);

	// For security - allow a jenkins server config to be locked to
	// non-system-admins
	@NotNull
	@Default("false")
	@Accessor("LOCKED")
	public Boolean getLocked();

	@Mutator("LOCKED")
	public void setLocked(Boolean isLocked);
}
