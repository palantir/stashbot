//   Copyright 2013 Palantir Technologies
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
package com.palantir.stash.stashbot.config;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Default;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

@Table("PAConfig001")
@Preload
public interface PluginAuthenticationConfiguration extends Entity {

    @NotNull
    @Unique
    public String getName();

    public void setName(String username);

    @NotNull
    @Default("stash-readonly-user")
    public String getUsername();

    public void setUsername(String stashUsername);

    @NotNull
    @Default("YOU SHOULD REALLY SET THIS TO SOMETHING ELSE")
    public String getPassword();

    public void setPassword(String stashPassword);

    @NotNull
    @Default("stash-readonly-user@example.com")
    public String getEmailAddress();

    public void setEmailAddress(String email);

}
