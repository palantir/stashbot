package com.palantir.stash.stashbot.jobtemplate;

import net.java.ao.Entity;
import net.java.ao.schema.Default;
import net.java.ao.schema.Table;

import com.palantir.stash.stashbot.config.RepositoryConfiguration;

@Table("JobMapping001")
public interface JobMapping extends Entity {

    // data access
    public RepositoryConfiguration getRepositoryConfiguration();

    public void setRepositoryConfiguration(RepositoryConfiguration rc);

    public JobTemplate getJobTemplate();

    public void setJobTemplate(JobTemplate jjt);

    @Default("true")
    public Boolean isVisible();

    public void setVisible(Boolean visible);

    @Default("false")
    public Boolean isEnabled();

    public void setEnabled(Boolean enabled);

}
